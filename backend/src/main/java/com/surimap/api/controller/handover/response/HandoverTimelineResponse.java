package com.surimap.api.controller.handover.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.surimap.api.controller.summary.response.SearchHistorySummaryItemResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HandoverTimelineResponse(
    UUID incidentId,
    UUID operationalPeriodId,
    ScopeResponse scope,
    List<ActorResponse> actors,
    List<PathResponse> paths,
    List<EventResponse> events,
    MetricsResponse metrics,
    SearchHistorySummaryItemResponse summary) {

  public HandoverTimelineResponse {
    actors = List.copyOf(actors);
    paths = List.copyOf(paths);
    events = List.copyOf(events);
  }

  public record ScopeResponse(
      String scopeType, UUID dutyShiftId, Instant startedAt, Instant endedAt) {}

  public record ActorResponse(String actorId, String displayName, String colorKey) {}

  public record PathResponse(
      UUID pathId,
      String actorId,
      String mode,
      Instant startedAt,
      Instant endedAt,
      List<PointResponse> points) {
    public PathResponse {
      points = List.copyOf(points);
    }
  }

  public record PointResponse(Instant at, BigDecimal lat, BigDecimal lng, Integer accuracyMeters) {}

  public record EventResponse(
      String eventId,
      Instant occurredAt,
      String type,
      String actorId,
      String label,
      Map<String, Object> detail) {
    public EventResponse {
      detail = detail == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
    }
  }

  public record MetricsResponse(
      long distanceMeters,
      long walkingDistanceMeters,
      long drivingDistanceMeters,
      BigDecimal averageSpeedKmh,
      int stoppedSegmentCount,
      int markerCount,
      int handoverMemoCount,
      String syncStatus) {}
}
