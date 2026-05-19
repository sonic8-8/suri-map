package com.surimap.opcomparison;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OpComparisonRegionEvidence(
    String factId,
    OpComparisonRegionFactType type,
    List<UUID> operationalPeriodIds,
    BigDecimal areaSquareMeters,
    Map<UUID, Instant> firstPassTimes,
    Map<UUID, Long> durationSeconds) {

  public OpComparisonRegionEvidence {
    operationalPeriodIds =
        operationalPeriodIds == null ? List.of() : List.copyOf(operationalPeriodIds);
    firstPassTimes = firstPassTimes == null ? Map.of() : Map.copyOf(firstPassTimes);
    durationSeconds = durationSeconds == null ? Map.of() : Map.copyOf(durationSeconds);
  }
}
