package com.surimap.opcomparison;

import java.util.Objects;

public record OpComparisonNarrativeResult(
    OpComparisonNarrativeStatus status, String observationsJson) {

  public OpComparisonNarrativeResult {
    Objects.requireNonNull(status, "status must not be null");
    if (status == OpComparisonNarrativeStatus.READY) {
      Objects.requireNonNull(observationsJson, "observationsJson must not be null when ready");
    }
  }

  public static OpComparisonNarrativeResult ready(String observationsJson) {
    return new OpComparisonNarrativeResult(OpComparisonNarrativeStatus.READY, observationsJson);
  }

  public static OpComparisonNarrativeResult failed() {
    return new OpComparisonNarrativeResult(OpComparisonNarrativeStatus.FAILED, null);
  }
}
