package com.surimap.domain.path.validation;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GpsPointValidationResult {

  private List<GpsPoint> acceptedPoints;
  private List<ExcludedPoint> excludedPoints;

  @Builder
  private GpsPointValidationResult(
      List<GpsPoint> acceptedPoints, List<ExcludedPoint> excludedPoints) {
    this.acceptedPoints = acceptedPoints;
    this.excludedPoints = excludedPoints;
  }

  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class ExcludedPoint {

    private GpsPoint point;
    private GpsPointExclusionReason reason;

    @Builder
    private ExcludedPoint(GpsPoint point, GpsPointExclusionReason reason) {
      this.point = point;
      this.reason = reason;
    }
  }

  public enum GpsPointExclusionReason {
    LOW_ACCURACY,
    CLOCK_SKEW,
    INVALID_SPEED,
    DISTANCE_JUMP
  }
}
