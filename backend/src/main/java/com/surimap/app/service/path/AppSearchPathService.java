package com.surimap.app.service.path;

import com.surimap.app.service.path.request.SearchPathLifecycleAction;
import com.surimap.app.service.path.request.SearchPathStartServiceRequest;
import com.surimap.app.service.path.request.SearchPathStatusUpdateServiceRequest;
import com.surimap.app.service.path.response.SearchPathStartServiceResponse;
import com.surimap.app.service.path.response.SearchPathStatusUpdateServiceResponse;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathEventType;
import com.surimap.domain.path.SearchPathLifecycleEvent;
import com.surimap.domain.path.SearchPathMapper;
import com.surimap.domain.path.SearchPathPublishRequest;
import com.surimap.domain.path.SearchPathStatus;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.domain.path.port.SearchPathEventPublisher;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

public class AppSearchPathService {

  private final OperationalPeriodQuery opQuery;
  private final SearchPathEventPublisher eventPublisher;
  private final SearchPathMapper searchPathMapper;
  private final IdempotentResponseCache idempotentResponseCache;
  private final Map<UUID, SearchPath> activePaths = new ConcurrentHashMap<>();

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
  public SearchPathStartServiceResponse start(SearchPathStartServiceRequest request) {
    requireIdempotencyKey(request.getIdempotencyKey());
    return idempotencyCache()
        .replayOrRun(
            "POST /api/search-paths",
            request.getIdempotencyKey(),
            request,
            201,
            SearchPathStartServiceResponse.class,
            () -> SearchPathStartServiceResponse.from(startNewPath(request)),
            this::metadataForStart);
  }

  private SearchPath startNewPath(SearchPathStartServiceRequest request) {
    CurrentOpResult currentOp =
        opQuery
            .current(request.getIncidentId())
            .orElseThrow(() -> new SearchPathGuardException("op_required"));
    if (!currentOp.opId().equals(request.getOpId())) {
      throw new SearchPathGuardException("op_mismatch");
    }
    UUID accountId = request.getAccountId();
    if (accountId == null) {
      throw new SearchPathGuardException("channel_not_allowed");
    }

    SearchPath path =
        SearchPath.builder()
            .id(request.getSearchPathId() == null ? UUID.randomUUID() : request.getSearchPathId())
            .incidentId(request.getIncidentId())
            .opId(request.getOpId())
            .policePhoneId(request.getPolicePhoneId())
            .accountId(accountId)
            .startedAt(request.getStartedAt())
            .build();

    persistStartedPath(path);
    publish(path, SearchPathEventType.SEARCH_PATH_STARTED);
    activePaths.put(path.getId(), path);

    return path;
  }

  @Transactional
  public SearchPathStatusUpdateServiceResponse updateStatus(
      SearchPathStatusUpdateServiceRequest request) {
    requireIdempotencyKey(request.getIdempotencyKey());
    return idempotencyCache()
        .replayOrRun(
            "PATCH /api/search-paths/" + request.getSearchPathId(),
            request.getIdempotencyKey(),
            request,
            200,
            SearchPathStatusUpdateServiceResponse.class,
            () -> SearchPathStatusUpdateServiceResponse.from(patchLoadedPath(request)),
            this::metadataForStatusUpdate);
  }

  private SearchPath patchLoadedPath(SearchPathStatusUpdateServiceRequest request) {
    if (request.getAccountId() == null) {
      throw new SearchPathGuardException("channel_not_allowed");
    }
    SearchPath current = loadPersistedPath(request.getSearchPathId());
    if (current == null) {
      current = activePaths.get(request.getSearchPathId());
    }
    if (current == null) {
      throw new SearchPathGuardException("write_conflict");
    }
    if (current.getAccountId() != null && !current.getAccountId().equals(request.getAccountId())) {
      throw new SearchPathGuardException("write_conflict");
    }
    return transition(current, request);
  }

  private SearchPath transition(SearchPath current, SearchPathStatusUpdateServiceRequest request) {
    SearchPathStatus nextStatus = nextStatus(current.getStatus(), request.getAction());
    Instant clientTs = request.getClientTs() == null ? Instant.now() : request.getClientTs();
    SearchPath patched =
        SearchPath.builder()
            .id(current.getId())
            .incidentId(current.getIncidentId())
            .opId(current.getOpId())
            .policePhoneId(request.getPolicePhoneId())
            .accountId(current.getAccountId())
            .status(nextStatus)
            .version(current.getVersion() + 1)
            .startedAt(current.getStartedAt())
            .endedAt(nextStatus == SearchPathStatus.ENDED ? clientTs : null)
            .build();

    persistLifecycleTransition(patched, eventName(request.getAction()), clientTs);
    publish(patched, publishEventType(request.getAction()));
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
    SearchPath persistedPath =
        path.toBuilder()
            .dutyShiftId(dutyShiftId)
            .accountId(accountId)
            .createdAt(path.getStartedAt())
            .updatedAt(path.getStartedAt())
            .build();
    searchPathMapper.insertPath(persistedPath);
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
        SearchPathLifecycleEvent.builder()
            .id(lifecycleEventId(path.getId(), eventType, path.getVersion()))
            .searchPathId(path.getId())
            .eventType(eventType)
            .clientTs(safeClientTs)
            .serverReceivedAt(serverReceivedAt)
            .actorPolicePhoneId(path.getPolicePhoneId())
            .version(path.getVersion())
            .createdAt(serverReceivedAt)
            .build());
  }

  private SearchPath loadPersistedPath(UUID searchPathId) {
    if (searchPathMapper == null) {
      return null;
    }
    return searchPathMapper.findPathById(searchPathId).orElse(null);
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
        SearchPathPublishRequest.builder()
            .eventType(eventType)
            .id(path.getId())
            .incidentId(path.getIncidentId())
            .opId(path.getOpId())
            .policePhoneId(path.getPolicePhoneId())
            .accountId(path.getAccountId())
            .status(path.getStatus())
            .version(path.getVersion())
            .build());
  }

  private UUID lifecycleEventId(UUID pathId, String eventType, long version) {
    String seed = "search-path-lifecycle:%s:%s:%d".formatted(pathId, eventType, version);
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  private ResponseMetadata metadataForStart(SearchPathStartServiceResponse response) {
    return new ResponseMetadata(
        response.getId().toString(),
        response.getStatus().name(),
        response.getVersion(),
        response.getVersion());
  }

  private ResponseMetadata metadataForStatusUpdate(SearchPathStatusUpdateServiceResponse response) {
    return new ResponseMetadata(
        response.getId().toString(),
        response.getStatus().name(),
        response.getVersion(),
        response.getVersion());
  }

  private IdempotentResponseCache idempotencyCache() {
    if (idempotentResponseCache == null) {
      throw new IllegalStateException("IdempotentResponseCache is required");
    }
    return idempotentResponseCache;
  }
}
