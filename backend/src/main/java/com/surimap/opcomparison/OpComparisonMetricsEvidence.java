package com.surimap.opcomparison;

import java.math.BigDecimal;

public record OpComparisonMetricsEvidence(
    long pathDistanceMeters,
    long walkingDistanceMeters,
    long drivingDistanceMeters,
    int walkingRatioPercent,
    BigDecimal averageSpeedKmh,
    int stoppedSegmentCount,
    long stoppedDurationSeconds,
    int markerCount,
    int handoverMemoCount) {}
