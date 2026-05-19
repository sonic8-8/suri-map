package com.surimap.opcomparison;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OpComparisonRegionFact(
    String factId,
    OpComparisonRegionFactType type,
    List<UUID> operationalPeriodIds,
    String geometryGeojson,
    BigDecimal areaSquareMeters,
    List<OpComparisonRegionOccupancy> occupancies) {

  public OpComparisonRegionFact {
    Objects.requireNonNull(factId, "factId must not be null");
    Objects.requireNonNull(type, "type must not be null");
    operationalPeriodIds =
        operationalPeriodIds == null ? List.of() : List.copyOf(operationalPeriodIds);
    occupancies = occupancies == null ? List.of() : List.copyOf(occupancies);
  }
}
