package com.surimap.incident.service;

import com.surimap.account.AccountIdentityCatalog;
import com.surimap.external.ExternalAssignment;
import com.surimap.external.ExternalIncident;
import com.surimap.external.ExternalIncidentAdapter;
import com.surimap.external.ExternalMissingPerson;
import com.surimap.external.ExternalSeedMarker;
import com.surimap.incident.domain.IncidentImportIdempotencyRecord;
import com.surimap.incident.domain.IncidentRecord;
import com.surimap.incident.domain.IncidentStatus;
import com.surimap.incident.event.IncidentCreatedEvent;
import com.surimap.incident.event.IncidentEventPublisher;
import com.surimap.incident.exception.IncidentApiException;
import com.surimap.incident.exception.IncidentImportDependencyException;
import com.surimap.incident.lifecycle.IncidentLifecycleGuard;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.marker.domain.port.ReferenceMarkerSeed;
import com.surimap.marker.domain.port.ReferenceMarkerSeed.SeedMarker;
import com.surimap.operationalperiod.command.InitialOperationalPeriodCreator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사건 가져오기 use case 본체.
 *
 * <p>흐름: 권한 검증 → idempotency 조회/예약 → 외부 mock-112 fetch → DB write (incident, missing_person,
 * incident_assignment, idempotency_record) → S8 OP1 자동 생성 → S5 초기 기준 마커 생성 → S4 INCIDENT_CREATED
 * publish. 한 트랜잭션({@link Transactional}) 안에서 처리되며 어느 단계가 실패해도 전체 롤백된다.
 *
 * <p>같은 {@code sourceIncidentId} 재호출은 기존 사건을 재사용하고, 같은 {@code Idempotency-Key} + 다른 body는 {@code
 * idempotency_mismatch}로 거부한다. S5/S8/S4 외부 의존은 fallback adapter 경유로 추상화되어 다른 Lane 머지 전에도 본 task가
 * 단독으로 빌드·테스트된다.
 */
@Service
public class IncidentImportService {

  private static final Logger log = LoggerFactory.getLogger(IncidentImportService.class);

  private static final String PRECINCT_FIRST_SOURCE_INCIDENT_ID =
      "00000000-0000-0000-0000-000000000001";
  private static final UUID PRECINCT_FIRST_INCIDENT_ID =
      UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final String IMPORT_REQUEST_PATH = "/api/incidents/import";
  private static final String IMPORT_REQUEST_METHOD = "POST";
  private static final long INITIAL_VERSION = 1L;

  private final IncidentImportAuthorizer authorizer;
  private final ExternalIncidentAdapter externalIncidentAdapter;
  private final InitialOperationalPeriodCreator initialOperationalPeriodCreator;
  private final ReferenceMarkerSeed referenceMarkerSeed;
  private final IncidentEventPublisher incidentEventPublisher;
  private final IncidentLifecycleGuard incidentLifecycleGuard;
  private final IncidentMapper incidentMapper;
  private final Clock clock;

  public IncidentImportService(
      IncidentImportAuthorizer authorizer,
      ExternalIncidentAdapter externalIncidentAdapter,
      InitialOperationalPeriodCreator initialOperationalPeriodCreator,
      ReferenceMarkerSeed referenceMarkerSeed,
      IncidentEventPublisher incidentEventPublisher,
      IncidentLifecycleGuard incidentLifecycleGuard,
      IncidentMapper incidentMapper,
      Clock clock) {
    this.authorizer = authorizer;
    this.externalIncidentAdapter = externalIncidentAdapter;
    this.initialOperationalPeriodCreator = initialOperationalPeriodCreator;
    this.referenceMarkerSeed = referenceMarkerSeed;
    this.incidentEventPublisher = incidentEventPublisher;
    this.incidentLifecycleGuard = incidentLifecycleGuard;
    this.incidentMapper = incidentMapper;
    this.clock = clock;
  }

  @Transactional
  public IncidentImportResult importIncident(IncidentImportCommand command) {
    authorizer.requireImportAllowed(command.authentication(), command.clientChannel());
    requireIdempotencyKey(command.idempotencyKey());
    Instant now = clock.instant();

    // 같은 sourceIncidentId는 112 원천 사건 1건을 의미한다. 새 Idempotency-Key로 재호출돼도
    // 외부 fetch나 하위 Lane port 호출을 반복하지 않고 기존 사건 응답만 재구성한다.
    var existing = incidentMapper.findBySourceIncidentId(command.sourceIncidentId());
    if (existing.isPresent()) {
      // 이미 terminal인 112 원천 사건은 import replay로 되살리지 않는다.
      incidentLifecycleGuard.requireOpen(existing.get().getId());
      reserveIdempotency(command, now);
      IncidentImportResult result = toResult(existing.get());
      completeIdempotency(command, result, now);
      markImported(existing.get().getSourceIncidentId());
      return result;
    }

    reserveIdempotency(command, now);
    ExternalIncident externalIncident = fetchExternalIncident(command.sourceIncidentId());
    String sourceIncidentId = sourceIncidentId(command, externalIncident);
    UUID incidentId = incidentIdFor(sourceIncidentId);

    incidentMapper.insertIncident(
        incidentId,
        sourceIncidentId,
        externalIncident.title(),
        IncidentStatus.OPEN.name(),
        toInstant(externalIncident.openedAt()),
        INITIAL_VERSION,
        now);
    insertMissingPerson(incidentId, externalIncident.missingPerson(), now);
    insertAssignments(incidentId, assignmentsOf(externalIncident), now);

    createOp1(incidentId);
    createReferenceMarkers(incidentId, externalIncident.seedMarkers());

    List<String> assignmentAccountIds = incidentMapper.findActiveAssignmentAccountIds(incidentId);
    incidentEventPublisher.publishIncidentCreated(
        new IncidentCreatedEvent(
            incidentId,
            IncidentStatus.OPEN.name(),
            INITIAL_VERSION,
            sourceIncidentId,
            assignmentAccountIds));

    IncidentImportResult result =
        new IncidentImportResult(
            incidentId,
            incidentId,
            IncidentStatus.OPEN.name(),
            INITIAL_VERSION,
            assignmentAccountIds);
    completeIdempotency(command, result, now);
    markImported(sourceIncidentId);
    return result;
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      return;
    }
    throw new IncidentApiException("write_conflict", HttpStatus.CONFLICT);
  }

  private IncidentImportResult toResult(IncidentRecord incident) {
    return new IncidentImportResult(
        incident.getId(),
        incident.getId(),
        incident.getStatus(),
        incident.getVersion(),
        incidentMapper.findActiveAssignmentAccountIds(incident.getId()));
  }

  private void reserveIdempotency(IncidentImportCommand command, Instant now) {
    String requestBodyHash = requestBodyHash(command.sourceIncidentId());
    var existing =
        incidentMapper.findImportIdempotencyRecord(
            command.idempotencyKey(), IMPORT_REQUEST_PATH, IMPORT_REQUEST_METHOD);
    if (existing.isPresent()) {
      // 같은 key로 다른 body를 보내면 외부 112 조회나 DB write 전에 차단한다.
      requireSameRequestBody(existing.get(), requestBodyHash);
      return;
    }

    incidentMapper.insertImportIdempotencyRecord(
        idempotencyRecordId(command.idempotencyKey()),
        command.idempotencyKey(),
        requestBodyHash,
        IMPORT_REQUEST_PATH,
        IMPORT_REQUEST_METHOD,
        "RESERVED",
        now);
  }

  private void requireSameRequestBody(
      IncidentImportIdempotencyRecord existing, String requestBodyHash) {
    if (requestBodyHash.equals(existing.getRequestBodyHash())) {
      return;
    }
    throw new IncidentApiException("idempotency_mismatch", HttpStatus.CONFLICT);
  }

  private void completeIdempotency(
      IncidentImportCommand command, IncidentImportResult result, Instant now) {
    incidentMapper.completeImportIdempotencyRecord(
        command.idempotencyKey(),
        IMPORT_REQUEST_PATH,
        IMPORT_REQUEST_METHOD,
        201,
        responseBodyJson(result),
        result.id(),
        result.status(),
        result.version(),
        now);
  }

  private String requestBodyHash(String sourceIncidentId) {
    // 현재 import body의 의미 필드는 sourceIncidentId 하나다. 요청 필드가 늘어나면 canonical body도
    // 같이 확장해야 같은 Idempotency-Key의 body mismatch를 정확히 잡을 수 있다.
    String canonicalBody = "{\"sourceIncidentId\":\"" + sourceIncidentId + "\"}";
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(canonicalBody.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }

  private UUID idempotencyRecordId(String idempotencyKey) {
    // idempotency_record.id도 deterministic하게 만들어 테스트 fixture와 중복 재시도를 같은 row로 수렴시킨다.
    return UUID.nameUUIDFromBytes(
        ("idempotency:" + IMPORT_REQUEST_PATH + ":" + idempotencyKey)
            .getBytes(StandardCharsets.UTF_8));
  }

  private String responseBodyJson(IncidentImportResult result) {
    return "{\"id\":\""
        + result.id()
        + "\",\"incidentId\":\""
        + result.incidentId()
        + "\",\"status\":\""
        + result.status()
        + "\",\"version\":"
        + result.version()
        + "}";
  }

  private ExternalIncident fetchExternalIncident(String sourceIncidentId) {
    try {
      return Objects.requireNonNull(externalIncidentAdapter.fetchIncident(sourceIncidentId));
    } catch (RuntimeException exception) {
      throw new IncidentImportDependencyException(exception);
    }
  }

  private void markImported(String sourceIncidentId) {
    try {
      externalIncidentAdapter.markImported(sourceIncidentId);
    } catch (RuntimeException exception) {
      log.warn("mock 112 imported mark failed: {}", exception.getMessage());
    }
  }

  private void insertMissingPerson(
      UUID incidentId, ExternalMissingPerson missingPerson, Instant importedAt) {
    if (missingPerson == null) {
      return;
    }
    incidentMapper.insertMissingPerson(
        incidentId,
        missingPerson.displayName(),
        missingPerson.photoObjectKey(),
        missingPerson.appearanceText(),
        missingPerson.lastSeenLocationText(),
        toInstant(missingPerson.lastSeenAt()),
        importedAt);
  }

  private void insertAssignments(
      UUID incidentId, List<ExternalAssignment> assignments, Instant importedAt) {
    for (ExternalAssignment assignment : assignments) {
      incidentMapper.insertIncidentAssignment(
          assignmentIdFor(assignment),
          incidentId,
          AccountIdentityCatalog.accountIdFromCodeOrUuid(assignment.accountCode()),
          assignment.incidentRole(),
          assignedAt(assignment, importedAt),
          importedAt);
    }
  }

  private void createOp1(UUID incidentId) {
    try {
      // OP1은 incident 생성 직후 필요한 하위 계약이다. 실패를 삼키면 OPEN 사건만 남는 partial import가 된다.
      initialOperationalPeriodCreator.createOp1(incidentId);
    } catch (RuntimeException exception) {
      throw new IncidentImportDependencyException(exception);
    }
  }

  private void createReferenceMarkers(UUID incidentId, List<ExternalSeedMarker> seedMarkers) {
    if (seedMarkers == null || seedMarkers.isEmpty()) {
      return;
    }
    try {
      // mock 112 raw payload는 Suri-Map marker ID를 들고 있지 않다. S5 port가 원천 marker 값을 받아
      // 자신이 소유한 marker row와 canonical fixture ID 매핑을 처리한다.
      referenceMarkerSeed.createForIncident(
          incidentId,
          seedMarkers.stream()
              .map(
                  marker ->
                      new SeedMarker(
                          marker.type(),
                          marker.source(),
                          marker.memo(),
                          marker.lon(),
                          marker.lat()))
              .toList());
    } catch (RuntimeException exception) {
      throw new IncidentImportDependencyException(exception);
    }
  }

  private List<ExternalAssignment> assignmentsOf(ExternalIncident incident) {
    if (incident.assignments() == null) {
      return Collections.emptyList();
    }
    return incident.assignments();
  }

  private String sourceIncidentId(
      IncidentImportCommand command, ExternalIncident externalIncident) {
    String sourceIncidentId = externalIncident.sourceIncidentId();
    if (sourceIncidentId == null || sourceIncidentId.isBlank()) {
      return command.sourceIncidentId();
    }
    return sourceIncidentId;
  }

  private UUID incidentIdFor(String sourceIncidentId) {
    if (PRECINCT_FIRST_SOURCE_INCIDENT_ID.equals(sourceIncidentId)) {
      // SC-01/02/10/12 대표 fixture는 다른 Lane 테스트가 같은 UUID를 참조하므로 고정 매핑을 유지한다.
      return PRECINCT_FIRST_INCIDENT_ID;
    }
    return UUID.nameUUIDFromBytes(
        ("incident:" + sourceIncidentId).getBytes(StandardCharsets.UTF_8));
  }

  private UUID assignmentIdFor(ExternalAssignment assignment) {
    String seed =
        assignment.externalAssignmentKey() == null
            ? assignment.accountCode()
            : assignment.externalAssignmentKey();
    // mock 112의 externalAssignmentKey가 있으면 그것을 우선해 polling/import 재실행 시 같은 배정 row로 수렴시킨다.
    return UUID.nameUUIDFromBytes(
        ("incident-assignment:" + Objects.requireNonNull(seed)).getBytes(StandardCharsets.UTF_8));
  }

  private Instant assignedAt(ExternalAssignment assignment, Instant fallback) {
    Instant assignedAt = toInstant(assignment.assignedAt());
    return assignedAt == null ? fallback : assignedAt;
  }

  private Instant toInstant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }
}
