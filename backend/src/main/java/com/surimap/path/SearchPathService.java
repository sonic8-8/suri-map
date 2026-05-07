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

    SearchPathAggregate aggregate =
        repository
            .findById(request.pathId())
            .orElseGet(
                () ->
                    repository.save(
                        new SearchPathAggregate(
                            request.pathId(), request.incidentId(), request.opId(), policePhoneId)));

    aggregate.appendAcceptedPoints(toAcceptedPoints(validationResult.acceptedPoints()));
    aggregate.appendExcludedPoints(toExcludedPoints(validationResult.excludedPoints()));
    aggregate.replaceSegments(autoSegments(aggregate.points()));
    aggregate.bumpVersion();
    repository.save(aggregate);

    eventPublisher.publishPathAppended(
        new PathAppendedPublishRequest(
            aggregate.id(), aggregate.status(), aggregate.version(), aggregate.opId(), aggregate.policePhoneId()));

    return new PathBatchAppendResponse(
        aggregate.id(),
        null,
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
    List<SearchPathSegment> segments = new ArrayList<>();
    if (points.isEmpty()) {
      return segments;
    }
    int start = 0;
    MovementType current = classify(points.get(0));
    for (int i = 1; i < points.size(); i++) {
      MovementType next = classify(points.get(i));
      if (next != current) {
        segments.add(segment(points, start, i - 1, current, segments.size()));
        start = i;
        current = next;
      }
    }
    segments.add(segment(points, start, points.size() - 1, current, segments.size()));
    return segments;
  }

  private SearchPathSegment segment(
      List<SearchPathPoint> points, int start, int end, MovementType type, int segmentIndex) {
    return new SearchPathSegment(
        "seg-%03d".formatted(segmentIndex + 1),
        type,
        start,
        end,
        points.get(start).pointId(),
        points.get(end).pointId());
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
