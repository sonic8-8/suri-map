package com.surimap.domain.path;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

public class SearchPathMetricsCalculator {

  public SearchPathMetrics calculate(
      List<SearchPath> paths, Instant durationStartedAt, Instant durationEndedAt) {
    long totalDistance = 0L;
    long walkingDistance = 0L;
    long drivingDistance = 0L;
    int stoppedSegments = 0;
    long stoppedDurationSeconds = 0L;
    Instant first = null;
    Instant last = null;

    for (SearchPath path : paths) {
      List<GpsPoint> points = path.getPoints();
      first = min(first, path.getStartedAt());
      last = max(last, path.getEndedAt());
      totalDistance +=
          points.isEmpty() ? distanceMeters(path.getGeometry()) : distanceMeters(points);
      for (SearchPathSegment segment : path.getSegments()) {
        long segmentDistance = segmentDistanceMeters(points, segment);
        if (segment.getMovementType() == MovementType.FOOT) {
          walkingDistance += segmentDistance;
        } else if (segment.getMovementType() == MovementType.VEHICLE) {
          drivingDistance += segmentDistance;
        } else if (segmentDistance == 0L) {
          stoppedSegments += 1;
          stoppedDurationSeconds += segmentDurationSeconds(points, segment);
        }
      }
    }

    Instant effectiveStart = durationStartedAt == null ? first : durationStartedAt;
    Instant effectiveEnd = durationEndedAt == null ? last : durationEndedAt;
    return SearchPathMetrics.builder()
        .distanceMeters(totalDistance)
        .walkingDistanceMeters(walkingDistance)
        .drivingDistanceMeters(drivingDistance)
        .averageSpeedKmh(averageSpeedKmh(totalDistance, effectiveStart, effectiveEnd))
        .stoppedSegmentCount(stoppedSegments)
        .stoppedDurationSeconds(stoppedDurationSeconds)
        .build();
  }

  private static long segmentDistanceMeters(List<GpsPoint> points, SearchPathSegment segment) {
    if (points.isEmpty()) {
      return distanceMeters(segment.getGeometry());
    }
    if (segment.getStartIndex() < 0
        || segment.getEndIndex() >= points.size()
        || segment.getEndIndex() < segment.getStartIndex()) {
      return 0L;
    }
    return distanceMeters(points.subList(segment.getStartIndex(), segment.getEndIndex() + 1));
  }

  private static long segmentDurationSeconds(List<GpsPoint> points, SearchPathSegment segment) {
    if (points.isEmpty()) {
      return durationSeconds(segment.getStartedAt(), segment.getEndedAt());
    }
    if (segment.getStartIndex() < 0
        || segment.getEndIndex() >= points.size()
        || segment.getEndIndex() < segment.getStartIndex()) {
      return 0L;
    }
    Instant startedAt = points.get(segment.getStartIndex()).getClientTs().toInstant();
    Instant endedAt = points.get(segment.getEndIndex()).getClientTs().toInstant();
    return durationSeconds(startedAt, endedAt);
  }

  private static long durationSeconds(Instant startedAt, Instant endedAt) {
    if (startedAt == null || endedAt == null || !endedAt.isAfter(startedAt)) {
      return 0L;
    }
    return Duration.between(startedAt, endedAt).getSeconds();
  }

  private static long distanceMeters(Geometry geometry) {
    if (geometry == null) {
      return 0L;
    }
    double distance = 0.0d;
    Coordinate[] coordinates = geometry.getCoordinates();
    for (int i = 1; i < coordinates.length; i++) {
      Coordinate previous = coordinates[i - 1];
      Coordinate current = coordinates[i];
      distance += haversineMeters(previous.y, previous.x, current.y, current.x);
    }
    return Math.round(distance);
  }

  private static long distanceMeters(List<GpsPoint> points) {
    double distance = 0.0d;
    for (int i = 1; i < points.size(); i++) {
      GpsPoint previous = points.get(i - 1);
      GpsPoint current = points.get(i);
      distance +=
          haversineMeters(
              previous.getLat().doubleValue(),
              previous.getLon().doubleValue(),
              current.getLat().doubleValue(),
              current.getLon().doubleValue());
    }
    return Math.round(distance);
  }

  private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
    double earthRadiusMeters = 6_371_000.0d;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double haversine =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
  }

  private static BigDecimal averageSpeedKmh(
      long distanceMeters, Instant startedAt, Instant endedAt) {
    if (distanceMeters <= 0L
        || startedAt == null
        || endedAt == null
        || !endedAt.isAfter(startedAt)) {
      return BigDecimal.ZERO.setScale(1);
    }
    double hours = Duration.between(startedAt, endedAt).toMillis() / 3_600_000.0d;
    return BigDecimal.valueOf(distanceMeters / 1000.0d / hours).setScale(1, RoundingMode.HALF_UP);
  }

  private static Instant min(Instant left, Instant right) {
    if (left == null) {
      return right;
    }
    if (right == null) {
      return left;
    }
    return left.isBefore(right) ? left : right;
  }

  private static Instant max(Instant left, Instant right) {
    if (left == null) {
      return right;
    }
    if (right == null) {
      return left;
    }
    return left.isAfter(right) ? left : right;
  }
}
