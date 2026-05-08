package com.surimap.incident.service;

import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.incident.domain.IncidentImportIdempotencyRecord;
import com.surimap.incident.domain.IncidentRecord;
import com.surimap.incident.domain.IncidentStatus;
import com.surimap.incident.event.IncidentClosedEvent;
import com.surimap.incident.event.IncidentEventPublisher;
import com.surimap.incident.exception.IncidentApiException;
import com.surimap.incident.lifecycle.IncidentLifecycleGuard;
import com.surimap.incident.repository.IncidentMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S1-1 사건 종료 command. terminal 전이, active PII 삭제, INCIDENT_CLOSED handoff를 한 트랜잭션에서 묶는다. */
@Service
public class IncidentCloseService {

  private static final String CLOSE_REQUEST_METHOD = "POST";
  private static final String WRITE_DISABLED_REASON = "incident_closed";

  private final IncidentMapper incidentMapper;
  private final IncidentLifecycleGuard incidentLifecycleGuard;
  private final IncidentEventPublisher incidentEventPublisher;
  private final Clock clock;

  public IncidentCloseService(
      IncidentMapper incidentMapper,
      IncidentLifecycleGuard incidentLifecycleGuard,
      IncidentEventPublisher incidentEventPublisher,
      Clock clock) {
    this.incidentMapper = incidentMapper;
    this.incidentLifecycleGuard = incidentLifecycleGuard;
    this.incidentEventPublisher = incidentEventPublisher;
    this.clock = clock;
  }

  @Transactional
  public IncidentCloseResult closeIncident(IncidentCloseCommand command) {
    requireIdempotencyKey(command.idempotencyKey());
    // confirm=false 요청은 close 의사결정이 아직 끝나지 않은 상태이므로 DB 예약도 만들지 않는다.
    requireConfirmed(command.confirmPersonalDataRemoval());

    Instant now = clock.instant();
    String requestPath = closeRequestPath(command.incidentId());
    String requestBodyHash =
        requestBodyHash(command.closeReason(), command.confirmPersonalDataRemoval());

    // 같은 Idempotency-Key replay는 이미 CLOSED가 된 뒤에도 같은 200 응답을 돌려줘야 하므로
    // lifecycle guard보다 먼저 cache를 확인한다. 새 key로 닫힌 사건에 쓰면 아래 guard가 incident_closed를 낸다.
    var replay = replayIfCompleted(command.idempotencyKey(), requestPath, requestBodyHash);
    if (replay != null) {
      return replay;
    }

    incidentMapper.insertImportIdempotencyRecord(
        idempotencyRecordId(requestPath, command.idempotencyKey()),
        command.idempotencyKey(),
        requestBodyHash,
        requestPath,
        CLOSE_REQUEST_METHOD,
        "RESERVED",
        now);

    incidentLifecycleGuard.requireOpen(command.incidentId());
    int updated =
        incidentMapper.closeIncident(
            command.incidentId(), closedByAccountId(command.authentication()), now);
    if (updated == 0) {
      incidentLifecycleGuard.requireOpen(command.incidentId());
      throw new IncidentApiException("write_conflict", HttpStatus.CONFLICT);
    }
    // SC-12 purge order: S1-1은 active DB의 실종자 PII와 photo pointer를 close 트랜잭션에서 즉시 제거한다.
    incidentMapper.deleteMissingPersonByIncidentId(command.incidentId());

    IncidentCloseResult result =
        incidentMapper
            .findByIncidentId(command.incidentId())
            .map(this::toResult)
            .orElseThrow(() -> new IncidentApiException("write_conflict", HttpStatus.CONFLICT));

    // S1-1은 S1-3 purge coordinator를 직접 호출하지 않는다. INCIDENT_CLOSED publish 요청이 handoff 경계다.
    incidentEventPublisher.publishIncidentClosed(
        new IncidentClosedEvent(
            result.id(),
            result.status(),
            result.version(),
            result.closedAt(),
            result.writeDisabledReason()));

    incidentMapper.completeImportIdempotencyRecord(
        command.idempotencyKey(),
        requestPath,
        CLOSE_REQUEST_METHOD,
        200,
        responseBodyJson(result),
        result.id(),
        result.status(),
        result.version(),
        now);
    return result;
  }

  private IncidentCloseResult replayIfCompleted(
      String idempotencyKey, String requestPath, String requestBodyHash) {
    var existing =
        incidentMapper.findImportIdempotencyRecord(
            idempotencyKey, requestPath, CLOSE_REQUEST_METHOD);
    if (existing.isEmpty()) {
      return null;
    }

    requireSameRequestBody(existing.get(), requestBodyHash);
    if (!"COMPLETED".equals(existing.get().getIdempotencyStatus())) {
      throw new IncidentApiException("write_conflict", HttpStatus.CONFLICT);
    }

    return incidentMapper
        .findByIncidentId(existing.get().getResultEntityId())
        .map(this::toResult)
        .orElseThrow(() -> new IncidentApiException("write_conflict", HttpStatus.CONFLICT));
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      return;
    }
    throw new IncidentApiException("write_conflict", HttpStatus.CONFLICT);
  }

  private void requireConfirmed(Boolean confirmPersonalDataRemoval) {
    if (Boolean.TRUE.equals(confirmPersonalDataRemoval)) {
      return;
    }
    throw new IncidentApiException("write_conflict", HttpStatus.CONFLICT);
  }

  private void requireSameRequestBody(
      IncidentImportIdempotencyRecord existing, String requestBodyHash) {
    if (requestBodyHash.equals(existing.getRequestBodyHash())) {
      return;
    }
    throw new IncidentApiException("idempotency_mismatch", HttpStatus.CONFLICT);
  }

  private IncidentCloseResult toResult(IncidentRecord incident) {
    return new IncidentCloseResult(
        incident.getId(),
        incident.getId(),
        IncidentStatus.CLOSED.name(),
        incident.getVersion(),
        incident.getClosedAt(),
        WRITE_DISABLED_REASON);
  }

  private UUID closedByAccountId(Authentication authentication) {
    if (!(authentication instanceof SuriMapAuthentication auth)) {
      throw new IncidentApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
    try {
      return UUID.fromString(auth.getAccountId());
    } catch (IllegalArgumentException exception) {
      // closed_by_account_id는 DB UUID 컬럼이다. fixture 계정명이 문자열이면 public close 계약 밖이므로 NULL로 둔다.
      return null;
    }
  }

  private String closeRequestPath(UUID incidentId) {
    return "/api/incidents/" + incidentId + "/close";
  }

  private UUID idempotencyRecordId(String requestPath, String idempotencyKey) {
    return UUID.nameUUIDFromBytes(
        ("idempotency:" + requestPath + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
  }

  private String requestBodyHash(String closeReason, Boolean confirmPersonalDataRemoval) {
    String canonicalBody =
        "{\"closeReason\":\""
            + escapeJson(closeReason)
            + "\",\"confirmPersonalDataRemoval\":"
            + Boolean.TRUE.equals(confirmPersonalDataRemoval)
            + "}";
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(canonicalBody.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }

  private String responseBodyJson(IncidentCloseResult result) {
    return "{\"id\":\""
        + result.id()
        + "\",\"incidentId\":\""
        + result.incidentId()
        + "\",\"status\":\""
        + result.status()
        + "\",\"version\":"
        + result.version()
        + ",\"closedAt\":\""
        + result.closedAt()
        + "\",\"writeDisabledReason\":\""
        + result.writeDisabledReason()
        + "\"}";
  }

  private String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
