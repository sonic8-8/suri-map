package com.surimap.opcomparison;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record OpComparisonDiffFact(
    String factId,
    OpComparisonDiffFactType type,
    String metricKey,
    UUID leftOperationalPeriodId,
    UUID rightOperationalPeriodId,
    BigDecimal leftValue,
    BigDecimal rightValue,
    BigDecimal delta,
    String threshold) {

  public OpComparisonDiffFact {
    Objects.requireNonNull(factId, "factId must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(metricKey, "metricKey must not be null");
    Objects.requireNonNull(leftOperationalPeriodId, "leftOperationalPeriodId must not be null");
    Objects.requireNonNull(rightOperationalPeriodId, "rightOperationalPeriodId must not be null");
    Objects.requireNonNull(leftValue, "leftValue must not be null");
    Objects.requireNonNull(rightValue, "rightValue must not be null");
    Objects.requireNonNull(delta, "delta must not be null");
    Objects.requireNonNull(threshold, "threshold must not be null");
  }
}
