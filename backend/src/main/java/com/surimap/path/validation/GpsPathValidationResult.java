package com.surimap.path.validation;

import java.util.List;

public record GpsPathValidationResult(
    List<GpsPathPoint> acceptedPoints, List<ExcludedPoint> excludedPoints) {

  public record ExcludedPoint(GpsPathPoint point, QualityReason reason) {}

  public enum QualityReason {
    LOW_ACCURACY,
    CLOCK_SKEW,
    INVALID_SPEED,
    DISTANCE_JUMP
  }
}
