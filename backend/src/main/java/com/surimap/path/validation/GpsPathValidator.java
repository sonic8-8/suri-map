package com.surimap.path.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GpsPathValidator {

  public GpsPathValidationResult validateBatch(
      List<GpsPathPoint> points, OffsetDateTime serverReceivedAt) {
    return validateBatch(points, serverReceivedAt, null);
  }

  public GpsPathValidationResult validateBatch(
      List<GpsPathPoint> points,
      OffsetDateTime serverReceivedAt,
      GpsPathValidationCriteria.GeoEnvelope activeOverallAreaEnvelope) {
    validateStructural(points);

    List<GpsPathPoint> accepted = new ArrayList<>();
    List<GpsPathValidationResult.ExcludedPoint> excluded = new ArrayList<>();

    for (GpsPathPoint point : points) {
      GpsPathValidationResult.QualityReason reason =
          detectQualityFailure(point, serverReceivedAt, accepted);
      if (reason == null) {
        accepted.add(point);
      } else {
        excluded.add(new GpsPathValidationResult.ExcludedPoint(point, reason));
      }
    }

    return new GpsPathValidationResult(List.copyOf(accepted), List.copyOf(excluded));
  }

  private void validateStructural(List<GpsPathPoint> points) {
    if (points == null || points.size() < GpsPathValidationCriteria.MIN_POINTS_PER_BATCH) {
      throw new InvalidGpsPathBatchException("points minItems=2");
    }
    if (points.size() > GpsPathValidationCriteria.MAX_POINTS_PER_BATCH) {
      throw new InvalidGpsPathBatchException("points maxItems=120");
    }
    if (!points.equals(
        points.stream().sorted(Comparator.comparing(GpsPathPoint::clientTs)).toList())) {
      throw new InvalidGpsPathBatchException("clientTs strict monotonic");
    }
    for (int i = 1; i < points.size(); i++) {
      if (!points.get(i).clientTs().isAfter(points.get(i - 1).clientTs())) {
        throw new InvalidGpsPathBatchException("clientTs strict monotonic");
      }
    }

    Set<String> uniquePointIds = new HashSet<>();
    for (GpsPathPoint point : points) {
      if (point.pointId() == null || point.pointId().isBlank()) {
        throw new InvalidGpsPathBatchException("pointId is required");
      }
      if (!uniquePointIds.add(point.pointId())) {
        throw new InvalidGpsPathBatchException("pointId must be unique");
      }
      validateCoordinate(point.lon(), point.lat());
    }
  }

  private void validateCoordinate(BigDecimal lon, BigDecimal lat) {
    if (lon == null || lat == null) {
      throw new InvalidGpsPathBatchException("null or NaN coordinate is a structural failure");
    }
    if (lon.scale() > GpsPathValidationCriteria.CANONICAL_COORDINATE_SCALE
        || lat.scale() > GpsPathValidationCriteria.CANONICAL_COORDINATE_SCALE) {
      throw new InvalidGpsPathBatchException("precision exceeds 6 decimal places");
    }

    BigDecimal canonicalLon =
        lon.setScale(GpsPathValidationCriteria.CANONICAL_COORDINATE_SCALE, RoundingMode.HALF_UP);
    BigDecimal canonicalLat =
        lat.setScale(GpsPathValidationCriteria.CANONICAL_COORDINATE_SCALE, RoundingMode.HALF_UP);

    boolean inLonRange =
        canonicalLon.compareTo(BigDecimal.valueOf(-180)) >= 0
            && canonicalLon.compareTo(BigDecimal.valueOf(180)) <= 0;
    boolean inLatRange =
        canonicalLat.compareTo(BigDecimal.valueOf(-90)) >= 0
            && canonicalLat.compareTo(BigDecimal.valueOf(90)) <= 0;
    if (!inLonRange || !inLatRange) {
      throw new InvalidGpsPathBatchException("lon/lat order must be EPSG:4326");
    }
  }

  private GpsPathValidationResult.QualityReason detectQualityFailure(
      GpsPathPoint point, OffsetDateTime serverReceivedAt, List<GpsPathPoint> accepted) {
    if (point.horizontalAccuracyM() != null
        && point.horizontalAccuracyM() > GpsPathValidationCriteria.MAX_HORIZONTAL_ACCURACY_METERS) {
      return GpsPathValidationResult.QualityReason.LOW_ACCURACY;
    }

    long skewSeconds = Math.abs(Duration.between(serverReceivedAt, point.clientTs()).getSeconds());
    if (skewSeconds > GpsPathValidationCriteria.MAX_TIMESTAMP_SKEW_SECONDS) {
      return GpsPathValidationResult.QualityReason.CLOCK_SKEW;
    }

    if (point.speedMps() == null
        || point.speedMps().compareTo(BigDecimal.ZERO) < 0
        || point.speedMps()
                .compareTo(BigDecimal.valueOf(GpsPathValidationCriteria.MAX_SPEED_METERS_PER_SECOND))
            > 0) {
      return GpsPathValidationResult.QualityReason.INVALID_SPEED;
    }

    if (!accepted.isEmpty()) {
      GpsPathPoint previousAccepted = accepted.get(accepted.size() - 1);
      long sampleSeconds =
          Duration.between(previousAccepted.clientTs(), point.clientTs()).getSeconds();
      if (sampleSeconds == 5) {
        double distanceMeters = distanceMeters(previousAccepted, point);
        if (distanceMeters > GpsPathValidationCriteria.MAX_DISTANCE_JUMP_METERS_PER_FIVE_SECONDS) {
          return GpsPathValidationResult.QualityReason.DISTANCE_JUMP;
        }
      }
    }

    return null;
  }

  private double distanceMeters(GpsPathPoint previous, GpsPathPoint current) {
    double earthRadiusMeters = 6_371_000.0;
    double previousLat = Math.toRadians(previous.lat().doubleValue());
    double currentLat = Math.toRadians(current.lat().doubleValue());
    double deltaLat = Math.toRadians(current.lat().subtract(previous.lat()).doubleValue());
    double deltaLon = Math.toRadians(current.lon().subtract(previous.lon()).doubleValue());
    double haversine =
        Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
            + Math.cos(previousLat)
                * Math.cos(currentLat)
                * Math.sin(deltaLon / 2)
                * Math.sin(deltaLon / 2);

    return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
  }
}
