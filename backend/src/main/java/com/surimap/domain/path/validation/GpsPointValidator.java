package com.surimap.domain.path.validation;

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
public class GpsPointValidator {

  public GpsPointValidationResult validate(
      List<GpsPoint> points, OffsetDateTime serverReceivedAt) {
    return validate(points, serverReceivedAt, null);
  }

  public GpsPointValidationResult validate(
      List<GpsPoint> points,
      OffsetDateTime serverReceivedAt,
      GpsPointValidationCriteria.GeoEnvelope activeOverallAreaEnvelope) {
    validateStructural(points);

    List<GpsPoint> accepted = new ArrayList<>();
    List<GpsPointValidationResult.ExcludedPoint> excluded = new ArrayList<>();

    for (GpsPoint point : points) {
      GpsPointValidationResult.GpsPointExclusionReason reason =
          detectQualityFailure(point, serverReceivedAt, accepted);
      if (reason == null) {
        accepted.add(point);
      } else {
        excluded.add(
            GpsPointValidationResult.ExcludedPoint.builder().point(point).reason(reason).build());
      }
    }

    return GpsPointValidationResult.builder()
        .acceptedPoints(List.copyOf(accepted))
        .excludedPoints(List.copyOf(excluded))
        .build();
  }

  private void validateStructural(List<GpsPoint> points) {
    if (points == null || points.size() < GpsPointValidationCriteria.MIN_POINTS_PER_BATCH) {
      throw new InvalidGpsPathBatchException("points minItems=2");
    }
    if (points.size() > GpsPointValidationCriteria.MAX_POINTS_PER_BATCH) {
      throw new InvalidGpsPathBatchException("points maxItems=120");
    }
    if (!points.equals(
        points.stream().sorted(Comparator.comparing(GpsPoint::getClientTs)).toList())) {
      throw new InvalidGpsPathBatchException("clientTs strict monotonic");
    }
    for (int i = 1; i < points.size(); i++) {
      if (!points.get(i).getClientTs().isAfter(points.get(i - 1).getClientTs())) {
        throw new InvalidGpsPathBatchException("clientTs strict monotonic");
      }
    }

    Set<String> uniquePointIds = new HashSet<>();
    for (GpsPoint point : points) {
      if (point.getPointId() == null || point.getPointId().isBlank()) {
        throw new InvalidGpsPathBatchException("pointId is required");
      }
      if (!uniquePointIds.add(point.getPointId())) {
        throw new InvalidGpsPathBatchException("pointId must be unique");
      }
      validateCoordinate(point.getLon(), point.getLat());
    }
  }

  private void validateCoordinate(BigDecimal lon, BigDecimal lat) {
    if (lon == null || lat == null) {
      throw new InvalidGpsPathBatchException("null or NaN coordinate is a structural failure");
    }
    if (lon.scale() > GpsPointValidationCriteria.CANONICAL_COORDINATE_SCALE
        || lat.scale() > GpsPointValidationCriteria.CANONICAL_COORDINATE_SCALE) {
      throw new InvalidGpsPathBatchException("precision exceeds 6 decimal places");
    }

    BigDecimal canonicalLon =
        lon.setScale(GpsPointValidationCriteria.CANONICAL_COORDINATE_SCALE, RoundingMode.HALF_UP);
    BigDecimal canonicalLat =
        lat.setScale(GpsPointValidationCriteria.CANONICAL_COORDINATE_SCALE, RoundingMode.HALF_UP);

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

  private GpsPointValidationResult.GpsPointExclusionReason detectQualityFailure(
      GpsPoint point, OffsetDateTime serverReceivedAt, List<GpsPoint> accepted) {
    if (point.getHorizontalAccuracyM() != null
        && point.getHorizontalAccuracyM()
            > GpsPointValidationCriteria.MAX_HORIZONTAL_ACCURACY_METERS) {
      return GpsPointValidationResult.GpsPointExclusionReason.LOW_ACCURACY;
    }

    long skewSeconds =
        Math.abs(Duration.between(serverReceivedAt, point.getClientTs()).getSeconds());
    if (skewSeconds > GpsPointValidationCriteria.MAX_TIMESTAMP_SKEW_SECONDS) {
      return GpsPointValidationResult.GpsPointExclusionReason.CLOCK_SKEW;
    }

    if (point.getSpeedMps() == null
        || point.getSpeedMps().compareTo(BigDecimal.ZERO) < 0
        || point
                .getSpeedMps()
                .compareTo(
                    BigDecimal.valueOf(GpsPointValidationCriteria.MAX_SPEED_METERS_PER_SECOND))
            > 0) {
      return GpsPointValidationResult.GpsPointExclusionReason.INVALID_SPEED;
    }

    if (!accepted.isEmpty()) {
      GpsPoint previousAccepted = accepted.get(accepted.size() - 1);
      long sampleSeconds =
          Duration.between(previousAccepted.getClientTs(), point.getClientTs()).getSeconds();
      if (sampleSeconds == 5) {
        double distanceMeters = distanceMeters(previousAccepted, point);
        if (distanceMeters > GpsPointValidationCriteria.MAX_DISTANCE_JUMP_METERS_PER_FIVE_SECONDS) {
          return GpsPointValidationResult.GpsPointExclusionReason.DISTANCE_JUMP;
        }
      }
    }

    return null;
  }

  private double distanceMeters(GpsPoint previous, GpsPoint current) {
    double earthRadiusMeters = 6_371_000.0;
    double previousLat = Math.toRadians(previous.getLat().doubleValue());
    double currentLat = Math.toRadians(current.getLat().doubleValue());
    double deltaLat = Math.toRadians(current.getLat().subtract(previous.getLat()).doubleValue());
    double deltaLon = Math.toRadians(current.getLon().subtract(previous.getLon()).doubleValue());
    double haversine =
        Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
            + Math.cos(previousLat)
                * Math.cos(currentLat)
                * Math.sin(deltaLon / 2)
                * Math.sin(deltaLon / 2);

    return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
  }
}
