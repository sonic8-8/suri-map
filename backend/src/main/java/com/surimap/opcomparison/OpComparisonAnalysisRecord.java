package com.surimap.opcomparison;

import java.time.Instant;
import java.util.UUID;

public record OpComparisonAnalysisRecord(
    UUID id,
    UUID incidentId,
    String operationalPeriodIdsJson,
    String requestHash,
    String sourceDataHash,
    OpComparisonAnalysisStatus status,
    String metricsJson,
    String diffFactsJson,
    String commonRegionsGeojson,
    OpComparisonNarrativeStatus narrativeStatus,
    String observationsJson,
    String failureReason,
    UUID requestedByAccountId,
    Instant requestedAt,
    Instant generatedAt,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
