package com.surimap.app.service.path;

import com.surimap.app.service.path.request.EndSearchPathServiceRequest;
import com.surimap.app.service.path.request.StartSearchPathServiceRequest;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathEventType;
import com.surimap.domain.path.SearchPathPublishRequest;
import com.surimap.domain.path.SearchPathStatus;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.domain.path.port.PolicePhoneGuard;
import com.surimap.domain.path.port.SearchPathEventPublisher;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.path.SearchPathMapper;
import com.surimap.path.SearchPathPersistenceRecord;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

public class AppSearchPathCommandService {

  private final OperationalPeriodQuery opQuery;
  private final PolicePhoneGuard policePhoneGuard;
  private final SearchPathEventPublisher eventPublisher;
  private final SearchPathMapper searchPathMapper;
  private final IdempotentResponseCache idempotentResponseCache;
  private final Map<UUID, SearchPath> activePaths = new ConcurrentHashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new ConcurrentHashMap<>();

  public AppSearchPathCommandService(
      OperationalPeriodQuery opQuery,
      PolicePhoneGuard policePhoneGuard,
      SearchPathEventPublisher eventPublisher) {
    this(opQuery, policePhoneGuard, eventPublisher, null);
  }

  public AppSearchPathCommandService(
      OperationalPeriodQuery opQuery,
      PolicePhoneGuard policePhoneGuard,
      SearchPathEventPublisher eventPublisher,
      SearchPathMapper searchPathMapper) {
    this(opQuery, policePhoneGuard, eventPublisher, searchPathMapper, (IdempotentResponseCache) null);
  }

  public AppSearchPathCommandService(
      OperationalPeriodQuery opQuery,
      PolicePhoneGuard policePhoneGuard,
      SearchPathEventPublisher eventPublisher,
      SearchPathMapper searchPathMapper,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this(
        opQuery,
        policePhoneGuard,
        eventPublisher,
        searchPathMapper,
        idempotentResponseCacheProvider.getIfAvailable());
  }

  private AppSearchPathCommandService(
      OperationalPeriodQuery opQuery,
      PolicePhoneGuard policePhoneGuard,
      SearchPathEventPublisher eventPublisher,
      SearchPathMapper searchPathMapper,
      IdempotentResponseCache idempotentResponseCache) {
    this.opQuery = opQuery;
    this.policePhoneGuard = policePhoneGuard;
    this.eventPublisher = eventPublisher;
    this.searchPathMapper = searchPathMapper;
    this.idempotentResponseCache = idempotentResponseCache;
  }

  @Transactional
  public SearchPath start(StartSearchPathServiceRequest request) {
    requireIdempotencyKey(request.idempotencyKey());
    String fingerprint = fingerprint("start", request);
    return replayOrRun(
        request.idempotencyKey(),
        fingerprint,
        "POST /api/search-paths",
        201,
        () -> startNewPath(request));
  }

  private SearchPath startNewPath(StartSearchPathServiceRequest request) {
    CurrentOpResult currentOp =
        opQuery
            .current(request.incidentId())
            .orElseThrow(() -> new SearchPathGuardException("op_required"));
    if (!currentOp.opId().equals(request.opId())) {
      throw new SearchPathGuardException("op_mismatch");
    }
    policePhoneGuard.requireAssigned(request.policePhoneId(), request.opId());

    SearchPath path =
        new SearchPath(
            request.searchPathId() == null ? UUID.randomUUID() : request.searchPathId(),
            request.incidentId(),
            request.opId(),
            request.policePhoneId(),
            SearchPathStatus.RECORDING,
            1L,
            request.startedAt(),
            null);

    persistStartedPath(path);
    eventPublisher.publish(
        new SearchPathPublishRequest(
            SearchPathEventType.SEARCH_PATH_STARTED,
            path.id(),
            path.opId(),
            path.policePhoneId(),
            path.status(),
            path.version()));
    activePaths.put(path.id(), path);

    return path;
  }

  @Transactional
  public SearchPath end(
      UUID searchPathId, UUID policePhoneId, EndSearchPathServiceRequest request) {
    requireIdempotencyKey(request.idempotencyKey());
    String fingerprint = fingerprint("end:" + searchPathId + ":" + policePhoneId, request);
    return replayOrRun(
        request.idempotencyKey(),
        fingerprint,
        "PATCH /api/search-paths/" + searchPathId,
        200,
        () -> endLoadedPath(searchPathId, policePhoneId, request));
  }

  private SearchPath endLoadedPath(
      UUID searchPathId, UUID policePhoneId, EndSearchPathServiceRequest request) {
    SearchPath current = activePaths.get(searchPathId);
    if (current == null) {
      current = loadPersistedPath(searchPathId);
    }
    if (current == null) {
      throw new SearchPathGuardException("write_conflict");
    }
    if (!current.policePhoneId().equals(policePhoneId)) {
      throw new SearchPathGuardException("police_phone_not_assigned");
    }
    policePhoneGuard.requireAssigned(policePhoneId, current.opId());
    return end(current, request);
  }

  @Transactional
  public SearchPath end(SearchPath current, EndSearchPathServiceRequest request) {
    if (current.status() == SearchPathStatus.ENDED) {
      throw new SearchPathGuardException("write_conflict");
    }
    SearchPath ended =
        new SearchPath(
            current.id(),
            current.incidentId(),
            current.opId(),
            current.policePhoneId(),
            SearchPathStatus.ENDED,
            current.version() + 1,
            current.startedAt(),
            request.endedAt());

    persistEndedPath(ended);
    eventPublisher.publish(
        new SearchPathPublishRequest(
            SearchPathEventType.SEARCH_PATH_ENDED,
            ended.id(),
            ended.opId(),
            ended.policePhoneId(),
            ended.status(),
            ended.version()));
    activePaths.put(ended.id(), ended);

    return ended;
  }

  private void persistStartedPath(SearchPath path) {
    if (searchPathMapper == null) {
      return;
    }
    UUID dutyShiftId =
        searchPathMapper
            .findActiveDutyShiftId(path.opId(), path.policePhoneId())
            .orElseThrow(() -> new SearchPathGuardException("police_phone_not_assigned"));
    searchPathMapper.insertPath(
        new SearchPathPersistenceRecord(
            path.id(),
            dutyShiftId,
            path.status().name(),
            path.startedAt(),
            path.endedAt(),
            (Geometry) null,
            path.version(),
            path.startedAt(),
            path.startedAt()));
  }

  private void persistEndedPath(SearchPath path) {
    if (searchPathMapper == null || searchPathMapper.findPathById(path.id()).isEmpty()) {
      return;
    }
    Instant endedAt = path.endedAt() == null ? Instant.now() : path.endedAt();
    searchPathMapper.endPath(path.id(), endedAt, path.version(), endedAt);
  }

  private SearchPath loadPersistedPath(UUID searchPathId) {
    if (searchPathMapper == null) {
      return null;
    }
    return searchPathMapper
        .findPathById(searchPathId)
        .map(
            row ->
                new SearchPath(
                    row.id(),
                    row.incidentId(),
                    row.opId(),
                    row.policePhoneId(),
                    SearchPathStatus.valueOf(row.status()),
                    row.version(),
                    row.startedAt(),
                    row.endedAt()))
        .orElse(null);
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new SearchPathGuardException("write_conflict");
    }
  }

  private SearchPath replayOrRun(
      String idempotencyKey,
      String fingerprint,
      String endpoint,
      int responseStatusCode,
      Operation operation) {
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          endpoint,
          idempotencyKey,
          fingerprint,
          responseStatusCode,
          SearchPath.class,
          operation::run,
          this::metadataFor);
    }
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyKey);
    if (existing != null) {
      if (!existing.fingerprint().equals(fingerprint)) {
        throw new SearchPathGuardException("idempotency_mismatch");
      }
      return existing.response();
    }
    SearchPath response = operation.run();
    idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(fingerprint, response));
    return response;
  }

  private ResponseMetadata metadataFor(SearchPath path) {
    return new ResponseMetadata(
        path.id().toString(), path.status().name(), path.version(), path.version());
  }

  private String fingerprint(String operation, Object request) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed =
          digest.digest(
              (operation + ":" + String.valueOf(request)).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  @FunctionalInterface
  private interface Operation {
    SearchPath run();
  }

  private record IdempotencyEntry(String fingerprint, SearchPath response) {}
}
