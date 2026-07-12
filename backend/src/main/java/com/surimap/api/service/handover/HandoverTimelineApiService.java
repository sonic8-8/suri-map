package com.surimap.api.service.handover;

import com.surimap.api.controller.handover.response.HandoverTimelineResponse;
import com.surimap.api.controller.handover.response.HandoverTimelineResponse.ActorResponse;
import com.surimap.api.controller.handover.response.HandoverTimelineResponse.EventResponse;
import com.surimap.api.controller.handover.response.HandoverTimelineResponse.MetricsResponse;
import com.surimap.api.controller.handover.response.HandoverTimelineResponse.PathResponse;
import com.surimap.api.controller.handover.response.HandoverTimelineResponse.PointResponse;
import com.surimap.api.controller.handover.response.HandoverTimelineResponse.ScopeResponse;
import com.surimap.api.controller.summary.response.SearchHistorySummaryItemResponse;
import com.surimap.api.service.path.SearchPathService;
import com.surimap.domain.path.GpsPoint;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathMetrics;
import com.surimap.domain.path.SearchPathMetricsCalculator;
import com.surimap.domain.path.SearchPathSegment;
import com.surimap.dutyshift.DutyShift;
import com.surimap.dutyshift.DutyShiftMapper;
import com.surimap.handover.query.HandoverMemoQuery;
import com.surimap.handover.query.HandoverMemoRow;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryFilters;
import com.surimap.marker.query.MarkerView;
import com.surimap.summary.SearchHistorySummaryMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HandoverTimelineApiService {

  private static final List<String> ACTOR_COLORS =
      List.of("blue", "green", "yellow", "pink", "purple", "gray");

  private final SearchPathService searchPathService;
  private final MarkerQuery markerQuery;
  private final HandoverMemoQuery handoverMemoQuery;
  private final SearchHistorySummaryMapper searchHistorySummaryMapper;
  private final DutyShiftMapper dutyShiftMapper;
  private final SearchPathMetricsCalculator metricsCalculator = new SearchPathMetricsCalculator();

  public HandoverTimelineApiService(
      SearchPathService searchPathService,
      MarkerQuery markerQuery,
      HandoverMemoQuery handoverMemoQuery,
      SearchHistorySummaryMapper searchHistorySummaryMapper,
      DutyShiftMapper dutyShiftMapper) {
    this.searchPathService =
        Objects.requireNonNull(searchPathService, "searchPathService must not be null");
    this.markerQuery = Objects.requireNonNull(markerQuery, "markerQuery must not be null");
    this.handoverMemoQuery =
        Objects.requireNonNull(handoverMemoQuery, "handoverMemoQuery must not be null");
    this.searchHistorySummaryMapper =
        Objects.requireNonNull(
            searchHistorySummaryMapper, "searchHistorySummaryMapper must not be null");
    this.dutyShiftMapper =
        Objects.requireNonNull(dutyShiftMapper, "dutyShiftMapper must not be null");
  }

  @Transactional(readOnly = true)
  public HandoverTimelineResponse get(
      UUID incidentId,
      UUID operationalPeriodId,
      String scopeType,
      UUID dutyShiftId,
      Instant startAt,
      Instant endAt,
      Boolean includeOtherActors) {
    Scope scope =
        Scope.resolve(
            incidentId,
            operationalPeriodId,
            scopeType,
            dutyShiftId,
            startAt,
            endAt,
            includeOtherActors,
            dutyShiftMapper);
    List<SearchPath> paths = scopedPaths(incidentId, operationalPeriodId, scope);
    List<MarkerView> markers = scopedMarkers(incidentId, operationalPeriodId, scope);
    List<HandoverMemoRow> memos = scopedMemos(incidentId, operationalPeriodId, scope);

    ActorRegistry actors = new ActorRegistry();
    List<PathResponse> pathResponses = pathResponses(paths, actors);
    List<EventResponse> events = events(paths, markers, memos, actors);
    MetricsResponse metrics = metrics(paths, markers, memos, scope, operationalPeriodId);
    SearchHistorySummaryItemResponse summary = summary(operationalPeriodId, incidentId, scope);

    return new HandoverTimelineResponse(
        incidentId,
        operationalPeriodId,
        new ScopeResponse(scope.type(), scope.dutyShiftId(), scope.startedAt(), scope.endedAt()),
        actors.responses(),
        pathResponses,
        events,
        metrics,
        summary);
  }

  private List<SearchPath> scopedPaths(UUID incidentId, UUID opId, Scope scope) {
    return searchPathService.findAll().stream()
        .filter(path -> incidentId.equals(path.getIncidentId()))
        .filter(path -> opId.equals(path.getOpId()))
        .filter(
            path -> scope.includes(path.getDutyShiftId(), path.getStartedAt(), path.getEndedAt()))
        .sorted(Comparator.comparing(HandoverTimelineApiService::pathStartOrEpoch))
        .toList();
  }

  private List<MarkerView> scopedMarkers(UUID incidentId, UUID opId, Scope scope) {
    return markerQuery
        .byIncident(incidentId, new MarkerQueryFilters(opId, null, null))
        .markers()
        .stream()
        .filter(
            marker ->
                scope.includes(marker.dutyShiftId(), marker.occurredAt(), marker.occurredAt()))
        .toList();
  }

  private List<HandoverMemoRow> scopedMemos(UUID incidentId, UUID opId, Scope scope) {
    return handoverMemoQuery.byContext(incidentId, opId, null, null).stream()
        .filter(memo -> scope.includes(memo.dutyShiftId(), memo.createdAt(), memo.createdAt()))
        .toList();
  }

  private List<PathResponse> pathResponses(List<SearchPath> paths, ActorRegistry actors) {
    List<PathResponse> responses = new ArrayList<>();
    for (SearchPath path : paths) {
      String actorId = actors.actorFor(path.getAccountId(), "현장 기록자");
      responses.add(
          new PathResponse(
              path.getId(),
              actorId,
              mode(path.getSegments()),
              path.getStartedAt(),
              path.getEndedAt(),
              path.getPoints().stream()
                  .map(
                      point ->
                          new PointResponse(
                              instant(point.getClientTs()),
                              point.getLat(),
                              point.getLon(),
                              point.getHorizontalAccuracyM()))
                  .toList()));
    }
    return responses;
  }

  private List<EventResponse> events(
      List<SearchPath> paths,
      List<MarkerView> markers,
      List<HandoverMemoRow> memos,
      ActorRegistry actors) {
    List<EventResponse> events = new ArrayList<>();
    for (SearchPath path : paths) {
      String actorId = actors.actorFor(path.getAccountId(), "현장 기록자");
      Instant startedAt = path.getStartedAt();
      if (startedAt != null) {
        events.add(
            new EventResponse(
                "path-start-" + path.getId(),
                startedAt,
                "PATH_START",
                actorId,
                "경로 시작",
                Map.of("pathId", path.getId().toString())));
      }
      for (SearchPathSegment segment : path.getSegments()) {
        Instant segmentStartedAt = segmentStartedAt(path.getPoints(), segment);
        if (segmentStartedAt != null) {
          events.add(
              new EventResponse(
                  "path-segment-" + segment.getId(),
                  segmentStartedAt,
                  "PATH_SEGMENT",
                  actorId,
                  movementLabel(segment.getMovementType()),
                  Map.of(
                      "pathId", path.getId().toString(),
                      "segmentId", segment.getId().toString(),
                      "movementType", segment.getMovementType().name())));
        }
      }
      if (path.getEndedAt() != null) {
        events.add(
            new EventResponse(
                "path-end-" + path.getId(),
                path.getEndedAt(),
                "PATH_END",
                actorId,
                "경로 종료",
                Map.of("pathId", path.getId().toString())));
      }
    }
    for (MarkerView marker : markers) {
      String actorId = actors.actorFor(marker.policePhoneId(), "현장 기록자");
      events.add(
          new EventResponse(
              "marker-" + marker.id(),
              marker.occurredAt(),
              "MARKER",
              actorId,
              "마커 기록",
              markerDetail(marker)));
    }
    for (HandoverMemoRow memo : memos) {
      String actorId = actors.actorFor(memo.createdByAccountId(), "메모 작성자");
      events.add(
          new EventResponse(
              "handover-memo-" + memo.memoId(),
              memo.createdAt(),
              "HANDOVER_MEMO",
              actorId,
              "인수인계 메모",
              Map.of(
                  "memoId",
                  memo.memoId().toString(),
                  "targetType",
                  memo.targetType(),
                  "targetId",
                  memo.targetId() == null ? "" : memo.targetId().toString(),
                  "content",
                  memo.content())));
    }
    return events.stream()
        .filter(event -> event.occurredAt() != null)
        .sorted(
            Comparator.comparing(EventResponse::occurredAt)
                .thenComparingInt(event -> eventRank(event.type()))
                .thenComparing(EventResponse::eventId))
        .toList();
  }

  private static int eventRank(String type) {
    return switch (type) {
      case "PATH_START" -> 0;
      case "PATH_SEGMENT" -> 1;
      case "MARKER" -> 2;
      case "HANDOVER_MEMO" -> 3;
      case "PATH_END" -> 4;
      default -> 9;
    };
  }

  private Map<String, Object> markerDetail(MarkerView marker) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("markerId", marker.id().toString());
    detail.put("markerType", marker.type().name());
    if (marker.supportRequestType() != null) {
      detail.put("supportRequestType", marker.supportRequestType().name());
    }
    if (marker.memo() != null && !marker.memo().isBlank()) {
      detail.put("memo", marker.memo());
    }
    if (marker.location() != null) {
      detail.put(
          "location",
          Map.of(
              "lat", BigDecimal.valueOf(marker.location().getY()),
              "lng", BigDecimal.valueOf(marker.location().getX())));
    }
    detail.put("photoCount", marker.photoSummary().size());
    return detail;
  }

  private MetricsResponse metrics(
      List<SearchPath> paths,
      List<MarkerView> markers,
      List<HandoverMemoRow> memos,
      Scope scope,
      UUID opId) {
    SearchPathMetrics pathMetrics =
        metricsCalculator.calculate(paths, scope.startedAt(), scope.endedAt());
    return new MetricsResponse(
        pathMetrics.getDistanceMeters(),
        pathMetrics.getWalkingDistanceMeters(),
        pathMetrics.getDrivingDistanceMeters(),
        pathMetrics.getAverageSpeedKmh(),
        pathMetrics.getStoppedSegmentCount(),
        markers.size(),
        memos.size(),
        syncStatus(opId, scope));
  }

  private SearchHistorySummaryItemResponse summary(UUID opId, UUID incidentId, Scope scope) {
    if ("RANGE".equals(scope.type())) {
      return null;
    }
    UUID dutyShiftId = "DUTY_SHIFT".equals(scope.type()) ? scope.dutyShiftId() : null;
    return searchHistorySummaryMapper
        .findByOp(opId, incidentId, scope.type(), scope.scopeId(opId), dutyShiftId, null)
        .stream()
        .findFirst()
        .map(SearchHistorySummaryItemResponse::from)
        .orElse(null);
  }

  private String syncStatus(UUID opId, Scope scope) {
    if ("RANGE".equals(scope.type())) {
      return "READY";
    }
    UUID dutyShiftId = "DUTY_SHIFT".equals(scope.type()) ? scope.dutyShiftId() : null;
    var summaries =
        searchHistorySummaryMapper.findByOp(
            opId, scope.incidentId(), scope.type(), scope.scopeId(opId), dutyShiftId, null);
    if (summaries.stream().anyMatch(row -> "PENDING_SYNC".equals(row.sourceReadiness()))) {
      return "PENDING_SYNC";
    }
    if (summaries.stream().anyMatch(row -> "STALE".equals(row.sourceReadiness()))) {
      return "STALE";
    }
    return "READY";
  }

  private static String mode(List<SearchPathSegment> segments) {
    List<MovementType> modes =
        segments.stream().map(SearchPathSegment::getMovementType).distinct().toList();
    if (modes.size() == 1) {
      return modes.get(0).name();
    }
    return modes.isEmpty() ? "UNKNOWN" : "MIXED";
  }

  private static String movementLabel(MovementType movementType) {
    return switch (movementType) {
      case FOOT -> "도보 구간";
      case VEHICLE -> "차량 구간";
      case UNKNOWN -> "이동 구간";
    };
  }

  private static Instant segmentStartedAt(List<GpsPoint> points, SearchPathSegment segment) {
    if (segment.getStartIndex() < 0 || segment.getStartIndex() >= points.size()) {
      return segment.getStartedAt();
    }
    return instant(points.get(segment.getStartIndex()).getClientTs());
  }

  private static Instant pathStartOrEpoch(SearchPath path) {
    return path.getStartedAt() == null ? Instant.EPOCH : path.getStartedAt();
  }

  private static Instant instant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  private static final class ActorRegistry {
    private static final String UNKNOWN_ACTOR_ID = "actor-unknown";

    private final Map<UUID, ActorResponse> actors = new LinkedHashMap<>();
    private boolean unknownActorReferenced;

    private String actorFor(UUID sourceId, String displayPrefix) {
      if (sourceId == null) {
        unknownActorReferenced = true;
        return UNKNOWN_ACTOR_ID;
      }
      return actors
          .computeIfAbsent(
              sourceId,
              ignored -> {
                int index = actors.size() + 1;
                return new ActorResponse(
                    actorId(sourceId),
                    displayPrefix + " " + index,
                    ACTOR_COLORS.get((index - 1) % ACTOR_COLORS.size()));
              })
          .actorId();
    }

    private List<ActorResponse> responses() {
      List<ActorResponse> responses = new ArrayList<>();
      if (unknownActorReferenced) {
        responses.add(new ActorResponse(UNKNOWN_ACTOR_ID, "알 수 없는 기록자", "gray"));
      }
      responses.addAll(actors.values());
      return List.copyOf(responses);
    }

    private static String actorId(UUID sourceId) {
      UUID hashed =
          UUID.nameUUIDFromBytes(("handover-actor:" + sourceId).getBytes(StandardCharsets.UTF_8));
      return "actor-" + hashed;
    }
  }

  private record Scope(
      UUID incidentId,
      String type,
      UUID dutyShiftId,
      Instant startedAt,
      Instant endedAt,
      boolean includeOtherActors) {

    private static Scope resolve(
        UUID incidentId,
        UUID opId,
        String scopeType,
        UUID dutyShiftId,
        Instant startAt,
        Instant endAt,
        Boolean includeOtherActors,
        DutyShiftMapper dutyShiftMapper) {
      String type =
          scopeType == null || scopeType.isBlank()
              ? "DUTY_SHIFT"
              : scopeType.toUpperCase(Locale.ROOT);
      if ("RANGE".equals(type)) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
          throw HandoverApiException.writeConflict();
        }
        return new Scope(
            incidentId, type, null, startAt, endAt, Boolean.TRUE.equals(includeOtherActors));
      }
      if ("OP".equals(type)) {
        return new Scope(incidentId, type, null, null, null, true);
      }
      if (!"DUTY_SHIFT".equals(type)) {
        throw HandoverApiException.writeConflict();
      }
      DutyShift dutyShift =
          dutyShiftId == null
              ? dutyShiftMapper.findByFilters(incidentId, opId, null, null, null).stream()
                  .findFirst()
                  .orElse(null)
              : dutyShiftMapper.findById(dutyShiftId).orElse(null);
      if (dutyShift == null
          || !incidentId.equals(dutyShift.getIncidentId())
          || !opId.equals(dutyShift.getOpId())) {
        throw HandoverApiException.writeConflict();
      }
      return new Scope(
          incidentId,
          type,
          dutyShift.getId(),
          dutyShift.getStartedAt(),
          dutyShift.getEndedAt(),
          Boolean.TRUE.equals(includeOtherActors));
    }

    private UUID scopeId(UUID opId) {
      return "DUTY_SHIFT".equals(type) ? dutyShiftId : opId;
    }

    private boolean includes(UUID rowDutyShiftId, Instant rowStart, Instant rowEnd) {
      if ("DUTY_SHIFT".equals(type) && rowDutyShiftId != null) {
        return dutyShiftId.equals(rowDutyShiftId)
            || (includeOtherActors && overlaps(rowStart, rowEnd));
      }
      if ("DUTY_SHIFT".equals(type)) {
        return overlaps(rowStart, rowEnd);
      }
      if ("RANGE".equals(type)) {
        return overlaps(rowStart, rowEnd);
      }
      return true;
    }

    private boolean overlaps(Instant rowStart, Instant rowEnd) {
      if (startedAt == null && endedAt == null) {
        return true;
      }
      if (rowStart == null && rowEnd == null) {
        return true;
      }
      Instant effectiveStart = rowStart == null ? rowEnd : rowStart;
      Instant effectiveEnd = rowEnd == null ? rowStart : rowEnd;
      if (startedAt != null && effectiveEnd != null && effectiveEnd.isBefore(startedAt)) {
        return false;
      }
      return endedAt == null || effectiveStart == null || !effectiveStart.isAfter(endedAt);
    }
  }
}
