package com.surimap.opcomparison;

import java.util.List;
import java.util.UUID;

public record OpComparisonEvidencePackage(
    UUID comparisonId,
    UUID incidentId,
    List<OpComparisonOperationalPeriodEvidence> operationalPeriods,
    List<OpComparisonDiffFact> diffFacts,
    List<OpComparisonRegionEvidence> regionFacts) {

  public OpComparisonEvidencePackage {
    operationalPeriods =
        operationalPeriods == null ? List.of() : List.copyOf(operationalPeriods);
    diffFacts = diffFacts == null ? List.of() : List.copyOf(diffFacts);
    regionFacts = regionFacts == null ? List.of() : List.copyOf(regionFacts);
  }
}
