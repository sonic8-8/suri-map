package com.surimap.app.service.path;

import com.surimap.app.service.path.request.EndSearchPathServiceRequest;
import com.surimap.app.service.path.request.PatchSearchPathServiceRequest;
import com.surimap.app.service.path.request.SearchPathLifecycleAction;
import com.surimap.app.service.path.request.StartSearchPathServiceRequest;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathEventType;
import com.surimap.domain.path.SearchPathPublishRequest;
import com.surimap.domain.path.SearchPathStatus;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.domain.path.port.SearchPathEventPublisher;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.domain.path.SearchPathLifecycleEventPersistenceRecord;
import com.surimap.domain.path.SearchPathMapper;
import com.surimap.domain.path.SearchPathPersistenceRecord;
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

public class AppSearchPathService {

  private final OperationalPeriodQuery opQuery;
  private final SearchPathEventPublisher eventPublisher;
  private final SearchPathMapper searchPathMapper;
  private final IdempotentResponseCache idempotentResponseCache;
  private final Map<UUID, SearchPath> activePaths = new ConcurrentHashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new ConcurrentHashMap<>();

  public AppSearchPathService(
      OperationalPeriodQuery opQuery, SearchPathEventPublisher eventPublisher) {
    this(opQuery, eventPublisher, null);
  }

  public AppSearchPathService(
      OperationalPeriodQuery opQuery,
      SearchPathEventPublisher eventPublisher,
      SearchPathMapper searchPathMapper) {
    this(opQuery, eventPublisher, searchPathMapper, (IdempotentResponseCache) null);
  }

  public AppSearchPathService(
      OperationalPeriodQuery opQuery,
      SearchPathEventPublisher eventPublisher,
      SearchPathMapper searchPathMapper,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this(
        opQuery,
        eventPublisher,
        searchPathMapper,
        idempotentResponseCacheProvider.getIfAvailable());
  }

  private AppSearchPathService(
      OperationalPeriodQuery opQuery,
      SearchPathEventPublisher eventPublisher,
      SearchPathMapper searchPathMapper,
      IdempotentResponseCache idempotentResponseCache) {
    this.opQuery = opQuery;
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
    UUID accountId = request.accountId();
    if (accountId == null) {
      throw new SearchPathGuardException("channel_not_allowed");
    }

    SearchPath path =
        SearchPath.builder()
            .id(request.searchPathId() == null ? UUID.randomUUID() : request.searchPathId())
            .incidentId(request.incidentId())
            .opId(request.opId())
            .policePhoneId(request.policePhoneId())
            .accountId(accountId)
            .startedAt(request.startedAt())
            .build();

    persistStartedPath(path);
    publish(path, SearchPathEventType.SEARCH_PATH_STARTED);
    activePaths.put(path.getId(), path);

    return path;
  }

  @Transactional
  public SearchPath patch(
      UUID searchPathId, UUID policePhoneId, PatchSearchPathServiceRequest request) {
    return patch(searchPathId, policePhoneId, null, request);
  }

  @Transactional
  public SearchPath patch(
      UUID searchPathId,
      UUID policePhoneId,
      UUID accountId,
      PatchSearchPathServiceRequest request) {
    requireIdempotencyKey(request.idempotencyKey());
    String fingerprint =
        fingerprint(
            "patch:" + request.action() + ":" + searchPathId + ":" + policePhoneId, request);
    return replayOrRun(
        request.idempotencyKey(),
        fingerprint,
        "PATCH /api/search-paths/" + searchPathId,
        200,
        () -> patchLoadedPath(searchPathId, policePhoneId, accountId, request));
  }

  @Transactional
  public SearchPath end(
      UUID searchPathId, UUID policePhoneId, EndSearchPathServiceRequest request) {
    return end(searchPathId, policePhoneId, null, request);
  }

  @Transactional
  public SearchPath end(
      UUID searchPathId, UUID policePhoneId, UUID accountId, EndSearchPathServiceRequest request) {
    return patch(
        searchPathId,
        policePhoneId,
        accountId,
        new PatchSearchPathServiceRequest(
            SearchPathLifecycleAction.END, request.endedAt(), request.idempotencyKey()));
  }

  private SearchPath patchLoadedPath(
      UUID searchPathId,
      UUID policePhoneId,
      UUID accountId,
      PatchSearchPathServiceRequest request) {
    if (accountId == null) {
      throw new SearchPathGuardException("channel_not_allowed");
    }
    SearchPath current = loadPersistedPath(searchPathId);
    if (current == null) {
      current = activePaths.get(searchPathId);
    }
    if (current == null) {
      throw new SearchPathGuardException("write_conflict");
    }
    if (current.getAccountId() != null && !current.getAccountId().equals(accountId)) {
      throw new SearchPathGuardException("write_conflict");
    }
    return transition(current, request, policePhoneId);
  }

  @Transactional
  public SearchPath end(SearchPath current, EndSearchPathServiceRequest request) {
    return transition(
        current,
        new PatchSearchPathServiceRequest(
            SearchPathLifecycleAction.END, request.endedAt(), request.idempotencyKey()));
  }

  @Transactional
  public SearchPath transition(SearchPath current, PatchSearchPathServiceRequest request) {
    return transition(current, request, current.getPolicePhoneId());
  }

  private SearchPath transition(
      SearchPath current, PatchSearchPathServiceRequest request, UUID actorPolicePhoneId) {
    SearchPathStatus nextStatus = nextStatus(current.getStatus(), request.action());
    Instant clientTs = request.clientTs() == null ? Instant.now() : request.clientTs();
    SearchPath patched =
        SearchPath.builder()
            .id(current.getId())
            .incidentId(current.getIncidentId())
            .opId(current.getOpId())
            .policePhoneId(actorPolicePhoneId)
            .accountId(current.getAccountId())
            .status(nextStatus)
            .version(current.getVersion() + 1)
            .startedAt(current.getStartedAt())
            .endedAt(nextStatus == SearchPathStatus.ENDED ? clientTs : null)
            .build();

    persistLifecycleTransition(patched, eventName(request.action()), clientTs);
    publish(patched, publishEventType(request.action()));
    activePaths.put(patched.getId(), patched);

    return patched;
  }

  private void persistStartedPath(SearchPath path) {
    if (searchPathMapper == null) {
      return;
    }
    UUID accountId = path.getAccountId();
    if (accountId == null) {
      throw new SearchPathGuardException("channel_not_allowed");
    }
    UUID dutyShiftId =
        searchPathMapper
            .findActiveDutyShiftIdByAccount(path.getOpId(), accountId)
            .orElseThrow(() -> new SearchPathGuardException("police_phone_not_assigned"));
    searchPathMapper.insertPath(
        new SearchPathPersistenceRecord(
            path.getId(),
            dutyShiftId,
            accountId,
            path.getStatus().name(),
            path.getStartedAt(),
            path.getEndedAt(),
            (Geometry) null,
            path.getVersion(),
            path.getStartedAt(),
            path.getStartedAt()));
    persistLifecycleEvent(path, "STARTED", path.getStartedAt(), Instant.now());
  }

  private void persistLifecycleTransition(SearchPath path, String eventType, Instant clientTs) {
    if (searchPathMapper == null || searchPathMapper.findPathById(path.getId()).isEmpty()) {
      return;
    }
    Instant updatedAt = Instant.now();
    searchPathMapper.updateLifecycleStatus(
        path.getId(), path.getStatus().name(), path.getEndedAt(), path.getVersion(), updatedAt);
    persistLifecycleEvent(path, eventType, clientTs, updatedAt);
  }

  private void persistLifecycleEvent(
      SearchPath path, String eventType, Instant clientTs, Instant serverReceivedAt) {
    if (searchPathMapper == null) {
      return;
    }
    Instant safeClientTs = clientTs == null ? serverReceivedAt : clientTs;
    searchPathMapper.insertLifecycleEvent(
        new SearchPathLifecycleEventPersistenceRecord(
            lifecycleEventId(path.getId(), eventType, path.getVersion()),
            path.getId(),
            eventType,
            safeClientTs,
            serverReceivedAt,
            path.getPolicePhoneId(),
            path.getVersion(),
            serverReceivedAt));
  }

  private SearchPath loadPersistedPath(UUID searchPathId) {
    if (searchPathMapper == null) {
      return null;
    }
    return searchPathMapper
        .findPathById(searchPathId)
        .map(
            row ->
                SearchPath.builder()
                    .id(row.id())
                    .incidentId(row.incidentId())
                    .opId(row.opId())
                    .policePhoneId(row.policePhoneId())
                    .accountId(row.accountId())
                    .status(SearchPathStatus.valueOf(row.status()))
                    .version(row.version())
                    .startedAt(row.startedAt())
                    .endedAt(row.endedAt())
                    .build())
        .orElse(null);
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new SearchPathGuardException("write_conflict");
    }
  }

  private SearchPathStatus nextStatus(
      SearchPathStatus currentStatus, SearchPathLifecycleAction action) {
    return switch (action) {
      case PAUSE -> {
        if (currentStatus != SearchPathStatus.RECORDING) {
          throw new SearchPathGuardException("write_conflict");
        }
        yield SearchPathStatus.PAUSED;
      }
      case RESUME -> {
        if (currentStatus != SearchPathStatus.PAUSED) {
          throw new SearchPathGuardException("write_conflict");
        }
        yield SearchPathStatus.RECORDING;
      }
      case END -> {
        if (currentStatus == SearchPathStatus.ENDED) {
          throw new SearchPathGuardException("write_conflict");
        }
        yield SearchPathStatus.ENDED;
      }
    };
  }

  private String eventName(SearchPathLifecycleAction action) {
    return switch (action) {
      case PAUSE -> "PAUSED";
      case RESUME -> "RESUMED";
      case END -> "ENDED";
    };
  }

  private SearchPathEventType publishEventType(SearchPathLifecycleAction action) {
    return switch (action) {
      case PAUSE -> SearchPathEventType.SEARCH_PATH_PAUSED;
      case RESUME -> SearchPathEventType.SEARCH_PATH_RESUMED;
      case END -> SearchPathEventType.SEARCH_PATH_ENDED;
    };
  }

  private void publish(SearchPath path, SearchPathEventType eventType) {
    eventPublisher.publish(
        new SearchPathPublishRequest(
            eventType,
            path.getId(),
            path.getIncidentId(),
            path.getOpId(),
            path.getPolicePhoneId(),
            path.getAccountId(),
            path.getStatus(),
            path.getVersion()));
  }

  private UUID lifecycleEventId(UUID pathId, String eventType, long version) {
    String seed = "search-path-lifecycle:%s:%s:%d".formatted(pathId, eventType, version);
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
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
        path.getId().toString(), path.getStatus().name(), path.getVersion(), path.getVersion());
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
