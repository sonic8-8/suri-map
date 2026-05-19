package com.surimap.opcomparison;

import java.util.List;
import java.util.Objects;

public record OpComparisonThresholdResult(
    List<OpComparisonDiffFact> diffFacts,
    List<OpComparisonRegionFact> regionFacts,
    OpComparisonNarrativeStatus narrativeStatus) {

  public OpComparisonThresholdResult {
    diffFacts = diffFacts == null ? List.of() : List.copyOf(diffFacts);
    regionFacts = regionFacts == null ? List.of() : List.copyOf(regionFacts);
    Objects.requireNonNull(narrativeStatus, "narrativeStatus must not be null");
  }
}
