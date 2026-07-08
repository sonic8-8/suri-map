package com.surimap.domain.path;

import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.SearchPathAggregate;
import com.surimap.domain.path.SearchPathPoint;
import com.surimap.domain.path.SearchPathSegment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class SearchPathMetricsCalculator {

  public SearchPathMetrics calculate(
      List<SearchPathAggregate> paths, Instant durationStartedAt, Instant durationEndedAt) {
    long totalDistance = 0L;
    long walkingDistance = 0L;
    long drivingDistance = 0L;
    int stoppedSegments = 0;
    long stoppedDurationSeconds = 0L;
    Instant first = null;
    Instant last = null;

    for (SearchPathAggregate path : paths) {
      List<SearchPathPoint> points = path.points();
      first = min(first, path.startedAt());
      last = max(last, path.endedAt());
      totalDistance += distanceMeters(points);
      for (SearchPathSegment segment : path.segments()) {
        long segmentDistance = segmentDistanceMeters(points, segment);
        if (segment.movementType() == MovementType.FOOT) {
          walkingDistance += segmentDistance;
        } else if (segment.movementType() == MovementType.VEHICLE) {
          drivingDistance += segmentDistance;
        } else if (segmentDistance == 0L) {
          stoppedSegments += 1;
          stoppedDurationSeconds += segmentDurationSeconds(points, segment);
        }
      }
    }

    Instant effectiveStart = durationStartedAt == null ? first : durationStartedAt;
    Instant effectiveEnd = durationEndedAt == null ? last : durationEndedAt;
    return new SearchPathMetrics(
        totalDistance,
        walkingDistance,
        drivingDistance,
        averageSpeedKmh(totalDistance, effectiveStart, effectiveEnd),
        stoppedSegments,
        stoppedDurationSeconds);
  }

  private static long segmentDistanceMeters(
      List<SearchPathPoint> points, SearchPathSegment segment) {
    if (segment.startIndex() < 0
        || segment.endIndex() >= points.size()
        || segment.endIndex() < segment.startIndex()) {
      return 0L;
    }
    return distanceMeters(points.subList(segment.startIndex(), segment.endIndex() + 1));
  }

  private static long segmentDurationSeconds(
      List<SearchPathPoint> points, SearchPathSegment segment) {
    if (segment.startIndex() < 0
        || segment.endIndex() >= points.size()
        || segment.endIndex() < segment.startIndex()) {
      return 0L;
    }
    Instant startedAt = points.get(segment.startIndex()).clientTs().toInstant();
    Instant endedAt = points.get(segment.endIndex()).clientTs().toInstant();
    if (!endedAt.isAfter(startedAt)) {
      return 0L;
    }
    return Duration.between(startedAt, endedAt).getSeconds();
  }

  private static long distanceMeters(List<SearchPathPoint> points) {
    double distance = 0.0d;
    for (int i = 1; i < points.size(); i++) {
      SearchPathPoint previous = points.get(i - 1);
      SearchPathPoint current = points.get(i);
      distance +=
          haversineMeters(
              previous.lat().doubleValue(),
              previous.lon().doubleValue(),
              current.lat().doubleValue(),
              current.lon().doubleValue());
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
