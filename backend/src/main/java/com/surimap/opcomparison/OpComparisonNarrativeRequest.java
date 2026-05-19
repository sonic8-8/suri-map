package com.surimap.opcomparison;

import java.util.Objects;

public record OpComparisonNarrativeRequest(OpComparisonEvidencePackage evidencePackage) {

  public OpComparisonNarrativeRequest {
    Objects.requireNonNull(evidencePackage, "evidencePackage must not be null");
  }
}
