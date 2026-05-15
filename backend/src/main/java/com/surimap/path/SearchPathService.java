package com.surimap.path;

import com.surimap.path.validation.GpsPathPoint;
import com.surimap.path.validation.GpsPathValidationResult.QualityReason;
import com.surimap.path.validation.GpsPathValidator;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SearchPathService {

  private static final double VEHICLE_MIN_SPEED = 5.0;
  private static final double FOOT_MIN_SPEED = 0.5;
  private static final double FOOT_MAX_SPEED = 2.5;

  private final SearchPathRepository repository;
  private final PathEventPublisher eventPublisher;
  private final GpsPathValidator gpsPathValidator;

  public SearchPathService(
      SearchPathRepository repository,
      PathEventPublisher eventPublisher,
      GpsPathValidator gpsPathValidator) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.gpsPathValidator = gpsPathValidator;
  }

  public PathBatchAppendResponse appendBatch(PathBatchAppendRequest request, UUID policePhoneId) {
    var validationResult =
        gpsPathValidator.validateBatch(
            toValidatorPoints(request.points()), request.points().get(0).clientTs().plusSeconds(20));

    List<SearchPathPoint> acceptedPoints = toAcceptedPoints(validationResult.acceptedPoints());
    List<PathExcludedPoint> excludedPoints = toExcludedPoints(validationResult.excludedPoints());

    SearchPathAggregate aggregate =
        repository
            .findById(request.pathId())
            .orElseGet(
                () ->
                    repository.save(
                        new SearchPathAggregate(
                            request.pathId(), request.incidentId(), request.opId(), policePhoneId)));

    int pointOffset = aggregate.points().size();
    int segmentOffset = aggregate.segments().size();
    List<SearchPathSegment> segments = new ArrayList<>(aggregate.segments());
    segments.addAll(autoSegments(acceptedPoints, pointOffset, segmentOffset));

    aggregate.appendAcceptedPoints(acceptedPoints);
    aggregate.appendExcludedPoints(excludedPoints);
    aggregate.replaceSegments(segments);
    aggregate.bumpVersion();
    aggregate = repository.save(aggregate);

    eventPublisher.publishPathAppended(
        new PathAppendedPublishRequest(
            aggregate.id(), aggregate.status(), aggregate.version(), aggregate.opId(), aggregate.policePhoneId()));

    return new PathBatchAppendResponse(
        aggregate.id(),
        aggregate.dutyShiftId(),
        aggregate.opId(),
        aggregate.policePhoneId(),
        validationResult.acceptedPoints().size(),
        validationResult.excludedPoints().size(),
        aggregate.excludedPoints(),
        toGeometry(aggregate.points()),
        aggregate.segments(),
        aggregate.version(),
        aggregate.status());
  }

  public PathQueryResponse query(UUID incidentId, UUID opId, UUID policePhoneId) {
    List<PathQueryRow> rows =
        repository.findAll().stream()
            .filter(path -> incidentId == null || incidentId.equals(path.incidentId()))
            .filter(path -> opId == null || opId.equals(path.opId()))
            .filter(path -> policePhoneId == null || policePhoneId.equals(path.policePhoneId()))
            .sorted(Comparator.comparing(SearchPathAggregate::version).reversed())
            .map(
                path ->
                    new PathQueryRow(
                        path.id(),
                        path.incidentId(),
                        path.opId(),
                        path.policePhoneId(),
                        path.status(),
                        path.version(),
                        toGeometry(path.points()),
                        path.segments(),
                        path.excludedPoints()))
            .toList();
    return new PathQueryResponse(rows);
  }

  public SegmentCorrectionResult correctSegment(
      String segmentId, MovementType movementType, UUID correctedByAccountId) {
    SearchPathAggregate owner =
        repository.findAll().stream()
            .filter(path -> path.segments().stream().anyMatch(segment -> segment.id().equals(segmentId)))
            .findFirst()
            .orElseThrow(() -> new SearchPathApiException("write_conflict"));

    SearchPathSegment corrected =
        owner.correctSegment(segmentId, movementType, correctedByAccountId, OffsetDateTime.now());
    owner.bumpVersion();
    repository.save(owner);

    eventPublisher.publishSegmentUpdated(
        new SearchPathSegmentUpdatedPublishRequest(
            owner.id(),
            owner.status(),
            owner.version(),
            owner.opId(),
            owner.policePhoneId(),
            corrected.id(),
            corrected.movementType(),
            corrected.movementTypeSource()));
    return new SegmentCorrectionResult(corrected, owner.opId(), owner.policePhoneId());
  }

  private List<GpsPathPoint> toValidatorPoints(List<PathBatchPointRequest> points) {
    return points.stream()
        .map(
            p ->
                new GpsPathPoint(
                    p.pointId(), p.clientTs(), p.lon(), p.lat(), p.speedMps(), p.horizontalAccuracyM()))
        .toList();
  }

  private List<SearchPathPoint> toAcceptedPoints(List<GpsPathPoint> points) {
    return points.stream()
        .map(
            p ->
                new SearchPathPoint(
                    p.pointId(), p.clientTs(), p.lon(), p.lat(), p.speedMps(), p.horizontalAccuracyM()))
        .toList();
  }

  private List<PathExcludedPoint> toExcludedPoints(
      List<com.surimap.path.validation.GpsPathValidationResult.ExcludedPoint> points) {
    return points.stream()
        .map(
            p ->
                new PathExcludedPoint(
                    p.point().pointId(), qualityReason(p.reason()), p.point().clientTs()))
        .toList();
  }

  private String qualityReason(QualityReason reason) {
    return switch (reason) {
      case LOW_ACCURACY -> "low_accuracy";
      case CLOCK_SKEW -> "clock_skew";
      case INVALID_SPEED -> "invalid_speed";
      case DISTANCE_JUMP -> "distance_jump";
    };
  }

  private List<SearchPathSegment> autoSegments(List<SearchPathPoint> points) {
    return autoSegments(points, 0, 0);
  }

  private List<SearchPathSegment> autoSegments(
      List<SearchPathPoint> points, int pointOffset, int segmentOffset) {
    List<SearchPathSegment> segments = new ArrayList<>();
    if (points.isEmpty()) {
      return segments;
    }
    List<MovementType> perPoint = new ArrayList<>();
    for (SearchPathPoint point : points) {
      perPoint.add(classify(point));
    }

    int runStart = 0;
    while (runStart < perPoint.size()) {
      MovementType runType = perPoint.get(runStart);
      int runEnd = runStart;
      while (runEnd + 1 < perPoint.size() && perPoint.get(runEnd + 1) == runType) {
        runEnd++;
      }
      if ((runType == MovementType.VEHICLE || runType == MovementType.FOOT)
          && (runEnd - runStart + 1) < 3) {
        for (int i = runStart; i <= runEnd; i++) {
          perPoint.set(i, MovementType.UNKNOWN);
        }
      }
      runStart = runEnd + 1;
    }

    int start = 0;
    MovementType current = perPoint.get(0);
    for (int i = 1; i < perPoint.size(); i++) {
      MovementType next = perPoint.get(i);
      if (next != current) {
        segments.add(
            segment(points, start, i - 1, current, pointOffset, segmentOffset + segments.size()));
        start = i;
        current = next;
      }
    }
    segments.add(
        segment(
            points,
            start,
            perPoint.size() - 1,
            current,
            pointOffset,
            segmentOffset + segments.size()));
    return segments;
  }

  private SearchPathSegment segment(
      List<SearchPathPoint> points,
      int start,
      int end,
      MovementType type,
      int pointOffset,
      int segmentIndex) {
    return new SearchPathSegment(
        "seg-%03d".formatted(segmentIndex + 1),
        1L,
        type,
        MovementTypeSource.AUTO,
        pointOffset + start,
        pointOffset + end,
        points.get(start).pointId(),
        points.get(end).pointId(),
        null,
        null);
  }

  private MovementType classify(SearchPathPoint point) {
    double speed = point.speedMps().doubleValue();
    if (speed >= VEHICLE_MIN_SPEED) {
      return MovementType.VEHICLE;
    }
    if (speed >= FOOT_MIN_SPEED && speed <= FOOT_MAX_SPEED) {
      return MovementType.FOOT;
    }
    return MovementType.UNKNOWN;
  }

  private List<List<Double>> toGeometry(List<SearchPathPoint> points) {
    return points.stream()
        .map(p -> List.of(p.lon().doubleValue(), p.lat().doubleValue()))
        .toList();
  }
}
