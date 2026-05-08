package com.surimap.path;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

public class SearchPathAggregate {

  private final UUID id;
  private final UUID incidentId;
  private final UUID opId;
  private final UUID policePhoneId;
  private SearchPathStatus status;
  private long version;
  private final List<SearchPathPoint> points;
  private final List<PathExcludedPoint> excludedPoints;
  private final List<SearchPathSegment> segments;

  public SearchPathAggregate(UUID id, UUID incidentId, UUID opId, UUID policePhoneId) {
    this.id = id;
    this.incidentId = incidentId;
    this.opId = opId;
    this.policePhoneId = policePhoneId;
    this.status = SearchPathStatus.RECORDING;
    this.version = 1L;
    this.points = new ArrayList<>();
    this.excludedPoints = new ArrayList<>();
    this.segments = new ArrayList<>();
  }

  public UUID id() {
    return id;
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
