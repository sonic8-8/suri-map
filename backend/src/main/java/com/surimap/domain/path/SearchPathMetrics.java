package com.surimap.domain.path;

import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchPathMetrics {

  private long distanceMeters;
  private long walkingDistanceMeters;
  private long drivingDistanceMeters;
  private BigDecimal averageSpeedKmh;
  private int stoppedSegmentCount;
  private long stoppedDurationSeconds;

  @Builder
  private SearchPathMetrics(
      long distanceMeters,
      long walkingDistanceMeters,
      long drivingDistanceMeters,
      BigDecimal averageSpeedKmh,
      int stoppedSegmentCount,
      long stoppedDurationSeconds) {
    this.distanceMeters = distanceMeters;
    this.walkingDistanceMeters = walkingDistanceMeters;
    this.drivingDistanceMeters = drivingDistanceMeters;
    this.averageSpeedKmh = averageSpeedKmh;
    this.stoppedSegmentCount = stoppedSegmentCount;
    this.stoppedDurationSeconds = stoppedDurationSeconds;
  }
}
