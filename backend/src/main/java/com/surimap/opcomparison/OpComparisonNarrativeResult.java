package com.surimap.opcomparison;

import java.util.Objects;

public record OpComparisonNarrativeResult(
    OpComparisonNarrativeStatus status, String observationsJson, String failureReason) {

  public static final String PROVIDER_FAILURE = "provider_failure";
  public static final String EMPTY_OUTPUT = "empty_output";
  public static final String SCHEMA_INVALID = "schema_invalid";
  public static final String FORBIDDEN_PHRASE = "forbidden_phrase";
  public static final String UNSUPPORTED_FACT_ID = "unsupported_fact_id";
  public static final String VALIDATION_REJECTED = "validation_rejected";

  public OpComparisonNarrativeResult {
    Objects.requireNonNull(status, "status must not be null");
    if (status == OpComparisonNarrativeStatus.READY) {
      Objects.requireNonNull(observationsJson, "observationsJson must not be null when ready");
      if (failureReason != null) {
        throw new IllegalArgumentException("failureReason must be null when ready");
      }
    } else {
      observationsJson = null;
      if (failureReason == null || failureReason.isBlank()) {
        failureReason = PROVIDER_FAILURE;
      }
    }
  }

  public static OpComparisonNarrativeResult ready(String observationsJson) {
    return new OpComparisonNarrativeResult(OpComparisonNarrativeStatus.READY, observationsJson, null);
  }

  public static OpComparisonNarrativeResult failed() {
    return failed(PROVIDER_FAILURE);
  }

  public static OpComparisonNarrativeResult failed(String failureReason) {
    return new OpComparisonNarrativeResult(OpComparisonNarrativeStatus.FAILED, null, failureReason);
  }
}
