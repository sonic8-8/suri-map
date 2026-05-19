package com.surimap.path;

import java.math.BigDecimal;

public record SearchPathMetrics(
    long distanceMeters,
    long walkingDistanceMeters,
    long drivingDistanceMeters,
    BigDecimal averageSpeedKmh,
    int stoppedSegmentCount,
    long stoppedDurationSeconds) {}
