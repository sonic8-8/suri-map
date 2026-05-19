package com.surimap.opcomparison;

import java.time.Instant;
import java.util.UUID;

public record OpComparisonRegionOccupancy(
    UUID operationalPeriodId, Instant firstObservedAt, Instant lastObservedAt, long durationSeconds) {}
