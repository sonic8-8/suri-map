package com.surimap.path;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SearchPathAggregate {

  private final UUID id;
  private final UUID dutyShiftId;
  private final UUID incidentId;
  private final UUID opId;
  private final UUID policePhoneId;
  private Instant startedAt;
  private Instant endedAt;
  private SearchPathStatus status;
  private long version;
  private final List<SearchPathPoint> points;
  private final List<PathExcludedPoint> excludedPoints;
  private final List<SearchPathSegment> segments;

  public SearchPathAggregate(UUID id, UUID incidentId, UUID opId, UUID policePhoneId) {
    this(
        id,
        null,
        incidentId,
        opId,
        policePhoneId,
        null,
        null,
        SearchPathStatus.RECORDING,
        1L,
        new ArrayList<>(),
        new ArrayList<>(),
        new ArrayList<>());
  }

  SearchPathAggregate(
      UUID id,
      UUID dutyShiftId,
      UUID incidentId,
      UUID opId,
      UUID policePhoneId,
      SearchPathStatus status,
      long version,
      List<SearchPathPoint> points,
      List<PathExcludedPoint> excludedPoints,
      List<SearchPathSegment> segments) {
    this(id, dutyShiftId, incidentId, opId, policePhoneId, null, null, status, version, points, excludedPoints, segments);
  }

  SearchPathAggregate(
      UUID id,
      UUID dutyShiftId,
      UUID incidentId,
      UUID opId,
      UUID policePhoneId,
      Instant startedAt,
      Instant endedAt,
      SearchPathStatus status,
      long version,
      List<SearchPathPoint> points,
      List<PathExcludedPoint> excludedPoints,
      List<SearchPathSegment> segments) {
    this.id = id;
    this.dutyShiftId = dutyShiftId;
    this.incidentId = incidentId;
    this.opId = opId;
    this.policePhoneId = policePhoneId;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.status = status;
    this.version = version;
    this.points = new ArrayList<>(points);
    this.excludedPoints = new ArrayList<>(excludedPoints);
    this.segments = new ArrayList<>(segments);
  }

  public UUID id() {
    return id;
  }

  public UUID dutyShiftId() {
    return dutyShiftId;
  }

  public UUID incidentId() {
    return incidentId;
  }

  public UUID opId() {
    return opId;
  }

  public UUID policePhoneId() {
    return policePhoneId;
  }

  public SearchPathStatus status() {
    return status;
  }

  public Instant startedAt() {
    if (startedAt != null) {
      return startedAt;
    }
    return points.stream().map(SearchPathPoint::clientTs).findFirst().map(OffsetDateTime::toInstant).orElse(null);
  }

  public Instant endedAt() {
    return endedAt;
  }

  public long version() {
    return version;
  }

  public List<SearchPathPoint> points() {
    return List.copyOf(points);
  }

  public List<PathExcludedPoint> excludedPoints() {
    return List.copyOf(excludedPoints);
  }

  public List<SearchPathSegment> segments() {
    return List.copyOf(segments);
  }

  public void appendAcceptedPoints(List<SearchPathPoint> acceptedPoints) {
    if (startedAt == null && !acceptedPoints.isEmpty()) {
      startedAt = acceptedPoints.get(0).clientTs().toInstant();
    }
    points.addAll(acceptedPoints);
  }

  public void replaceSegments(List<SearchPathSegment> nextSegments) {
    segments.clear();
    segments.addAll(nextSegments);
  }

  public void appendExcludedPoints(List<PathExcludedPoint> points) {
    excludedPoints.addAll(points);
  }

  public void bumpVersion() {
    version += 1L;
  }

  public SearchPathSegment correctSegment(
      String segmentId, MovementType movementType, UUID correctedByAccountId, OffsetDateTime correctedAt) {
    for (int i = 0; i < segments.size(); i++) {
      SearchPathSegment current = segments.get(i);
      if (current.id().equals(segmentId)) {
        SearchPathSegment corrected =
            new SearchPathSegment(
                current.id(),
                current.version() + 1L,
                movementType,
                MovementTypeSource.MANUAL,
                current.startIndex(),
                current.endIndex(),
                current.startPointId(),
                current.endPointId(),
                correctedByAccountId,
                correctedAt);
        segments.set(i, corrected);
        return corrected;
      }
    }
    throw new SearchPathApiException("write_conflict");
  }
}
