package com.surimap.domain.path.validation;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GpsPathValidationResult {

  private List<GpsPathPoint> acceptedPoints;
  private List<ExcludedPoint> excludedPoints;

  @Builder
  private GpsPathValidationResult(
      List<GpsPathPoint> acceptedPoints, List<ExcludedPoint> excludedPoints) {
    this.acceptedPoints = acceptedPoints;
    this.excludedPoints = excludedPoints;
  }

  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class ExcludedPoint {

    private GpsPathPoint point;
    private QualityReason reason;

    @Builder
    private ExcludedPoint(GpsPathPoint point, QualityReason reason) {
      this.point = point;
      this.reason = reason;
    }
  }

  public enum QualityReason {
    LOW_ACCURACY,
    CLOCK_SKEW,
    INVALID_SPEED,
    DISTANCE_JUMP
  }
}
