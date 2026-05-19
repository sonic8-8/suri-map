package com.surimap.opcomparison;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OpComparisonOperationalPeriodMetrics(
    UUID operationalPeriodId,
    int sequenceNumber,
    Instant startedAt,
    Instant endedAt,
    long pathDistanceMeters,
    long walkingDistanceMeters,
    long drivingDistanceMeters,
    int walkingRatioPercent,
    BigDecimal averageSpeedKmh,
    int stoppedSegmentCount,
    long stoppedDurationSeconds,
    int markerCount,
    int handoverMemoCount) {}
