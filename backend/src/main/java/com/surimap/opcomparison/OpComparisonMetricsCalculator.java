package com.surimap.opcomparison;

import com.surimap.domain.path.SearchPathMetrics;
import com.surimap.domain.path.SearchPathMetricsCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public class OpComparisonMetricsCalculator {

  private final SearchPathMetricsCalculator pathMetricsCalculator;

  public OpComparisonMetricsCalculator() {
    this(new SearchPathMetricsCalculator());
  }

  OpComparisonMetricsCalculator(SearchPathMetricsCalculator pathMetricsCalculator) {
    this.pathMetricsCalculator =
        Objects.requireNonNull(pathMetricsCalculator, "pathMetricsCalculator must not be null");
  }

  public List<OpComparisonOperationalPeriodMetrics> calculate(
      List<OpComparisonMetricsSource> sources) {
    if (sources == null || sources.isEmpty()) {
      return List.of();
    }
    return sources.stream().map(this::calculate).toList();
  }

  private OpComparisonOperationalPeriodMetrics calculate(OpComparisonMetricsSource source) {
    SearchPathMetrics pathMetrics =
        pathMetricsCalculator.calculate(source.paths(), source.startedAt(), source.endedAt());
    return new OpComparisonOperationalPeriodMetrics(
        source.operationalPeriodId(),
        source.sequenceNumber(),
        source.startedAt(),
        source.endedAt(),
        pathMetrics.getDistanceMeters(),
        pathMetrics.getWalkingDistanceMeters(),
        pathMetrics.getDrivingDistanceMeters(),
        walkingRatioPercent(
            pathMetrics.getWalkingDistanceMeters(), pathMetrics.getDistanceMeters()),
        pathMetrics.getAverageSpeedKmh(),
        pathMetrics.getStoppedSegmentCount(),
        pathMetrics.getStoppedDurationSeconds(),
        source.markerCount(),
        source.handoverMemoCount());
  }

  private static int walkingRatioPercent(long walkingDistanceMeters, long totalDistanceMeters) {
    if (walkingDistanceMeters <= 0L || totalDistanceMeters <= 0L) {
      return 0;
    }
    return BigDecimal.valueOf(walkingDistanceMeters)
        .multiply(BigDecimal.valueOf(100L))
        .divide(BigDecimal.valueOf(totalDistanceMeters), 0, RoundingMode.HALF_UP)
        .intValue();
  }
}
