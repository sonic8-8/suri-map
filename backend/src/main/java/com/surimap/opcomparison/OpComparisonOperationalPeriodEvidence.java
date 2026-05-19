package com.surimap.opcomparison;

import java.time.Instant;
import java.util.UUID;

public record OpComparisonOperationalPeriodEvidence(
    UUID operationalPeriodId,
    int sequenceNumber,
    Instant startedAt,
    Instant endedAt,
    OpComparisonMetricsEvidence metrics) {}
