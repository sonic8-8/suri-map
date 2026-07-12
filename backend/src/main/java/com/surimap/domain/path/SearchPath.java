package com.surimap.domain.path;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Geometry;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchPath {

  private UUID id;
  private UUID dutyShiftId;
  private UUID incidentId;
  private UUID opId;
  private UUID accountId;
  private Instant startedAt;
  private Instant endedAt;
  private SearchPathStatus status = SearchPathStatus.RECORDING;
  private long version = 1L;
  private Geometry geometry;
  private Instant createdAt;
  private Instant updatedAt;
  private List<SearchPathPoint> points = new ArrayList<>();
  private List<SearchPathExcludedPoint> excludedPoints = new ArrayList<>();
  private List<SearchPathSegment> segments = new ArrayList<>();

  @Builder(toBuilder = true)
  private SearchPath(
      UUID id,
      UUID dutyShiftId,
      UUID incidentId,
      UUID opId,
      UUID accountId,
      Instant startedAt,
      Instant endedAt,
      SearchPathStatus status,
      Long version,
      Geometry geometry,
      Instant createdAt,
      Instant updatedAt,
      List<SearchPathPoint> points,
      List<SearchPathExcludedPoint> excludedPoints,
      List<SearchPathSegment> segments) {
    this.id = id;
    this.dutyShiftId = dutyShiftId;
    this.incidentId = incidentId;
    this.opId = opId;
    this.accountId = accountId;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.status = status == null ? SearchPathStatus.RECORDING : status;
    this.version = version == null ? 1L : version;
    this.geometry = geometry;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.points = new ArrayList<>(emptyIfNull(points));
    this.excludedPoints = new ArrayList<>(emptyIfNull(excludedPoints));
    this.segments = new ArrayList<>(emptyIfNull(segments));
  }

  private static <T> List<T> emptyIfNull(List<T> values) {
    return values == null ? List.of() : values;
  }

  public Instant getStartedAt() {
    if (startedAt != null) {
      return startedAt;
    }
    return points.stream()
        .map(SearchPathPoint::getClientTs)
        .findFirst()
        .map(OffsetDateTime::toInstant)
        .orElse(null);
  }

  public void appendAcceptedPoints(List<SearchPathPoint> acceptedPoints) {
    if (startedAt == null && !acceptedPoints.isEmpty()) {
      startedAt = acceptedPoints.get(0).getClientTs().toInstant();
    }
    points.addAll(acceptedPoints);
  }

  public void replaceSegments(List<SearchPathSegment> nextSegments) {
    segments.clear();
    segments.addAll(nextSegments);
  }

  public void appendExcludedPoints(List<SearchPathExcludedPoint> points) {
    excludedPoints.addAll(points);
  }

  public void bumpVersion() {
    version += 1L;
  }

  public SearchPathSegment correctSegment(
      String segmentId,
      MovementType movementType,
      UUID correctedByAccountId,
      OffsetDateTime correctedAt) {
    for (int i = 0; i < segments.size(); i++) {
      SearchPathSegment current = segments.get(i);
      if (current.getId() != null && current.getId().toString().equals(segmentId)) {
        current.correct(movementType, correctedByAccountId, correctedAt);
        return current;
      }
    }
    throw new SearchPathApiException("write_conflict");
  }
}
