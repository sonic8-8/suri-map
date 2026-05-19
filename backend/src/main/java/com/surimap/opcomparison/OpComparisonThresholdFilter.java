package com.surimap.opcomparison;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpComparisonThresholdFilter {

  private static final BigDecimal DISTANCE_DELTA_METERS = BigDecimal.valueOf(300);
  private static final BigDecimal DISTANCE_DELTA_PERCENT = BigDecimal.valueOf(15);
  private static final BigDecimal WALKING_RATIO_DELTA_PERCENT_POINTS = BigDecimal.valueOf(20);
  private static final BigDecimal AVERAGE_SPEED_DELTA_KMH = BigDecimal.valueOf(1.0);
  private static final BigDecimal STOPPED_SEGMENT_DELTA_COUNT = BigDecimal.valueOf(2);
  private static final BigDecimal STOPPED_DURATION_DELTA_SECONDS = BigDecimal.valueOf(600);
  private static final BigDecimal MARKER_DELTA_COUNT = BigDecimal.valueOf(2);
  private static final BigDecimal HANDOVER_MEMO_DELTA_COUNT = BigDecimal.valueOf(2);
  private static final BigDecimal REGION_AREA_SQUARE_METERS = BigDecimal.valueOf(50);
  private static final BigDecimal REGION_TIME_DELTA_SECONDS = BigDecimal.valueOf(1_800);

  public OpComparisonThresholdResult filter(
      List<OpComparisonOperationalPeriodMetrics> metrics,
      List<OpComparisonRegionFact> regionFacts) {
    List<OpComparisonDiffFact> diffFacts = new ArrayList<>();
    diffFacts.addAll(metricDiffFacts(metrics == null ? List.of() : metrics));

    List<OpComparisonRegionFact> filteredRegions =
        (regionFacts == null ? List.<OpComparisonRegionFact>of() : regionFacts).stream()
            .filter(this::passesRegionAreaThreshold)
            .toList();
    diffFacts.addAll(regionTimeDiffFacts(filteredRegions));

    OpComparisonNarrativeStatus narrativeStatus =
        diffFacts.isEmpty() && filteredRegions.isEmpty()
            ? OpComparisonNarrativeStatus.SKIPPED
            : OpComparisonNarrativeStatus.GENERATING;
    return new OpComparisonThresholdResult(diffFacts, filteredRegions, narrativeStatus);
  }

  private List<OpComparisonDiffFact> metricDiffFacts(
      List<OpComparisonOperationalPeriodMetrics> metrics) {
    if (metrics.size() < 2) {
      return List.of();
    }
    List<OpComparisonDiffFact> facts = new ArrayList<>();
    for (int i = 0; i < metrics.size(); i++) {
      for (int j = i + 1; j < metrics.size(); j++) {
        OpComparisonOperationalPeriodMetrics left = metrics.get(i);
        OpComparisonOperationalPeriodMetrics right = metrics.get(j);
        addDistanceDiff(facts, left, right);
        addMetricDiff(
            facts,
            "walking-ratio",
            "walkingRatioPercent",
            value(left.walkingRatioPercent()),
            value(right.walkingRatioPercent()),
            WALKING_RATIO_DELTA_PERCENT_POINTS,
            ">=20%p",
            left,
            right);
        addMetricDiff(
            facts,
            "average-speed",
            "averageSpeedKmh",
            value(left.averageSpeedKmh()),
            value(right.averageSpeedKmh()),
            AVERAGE_SPEED_DELTA_KMH,
            ">=1.0km/h",
            left,
            right);
        addMetricDiff(
            facts,
            "stopped-segment-count",
            "stoppedSegmentCount",
            value(left.stoppedSegmentCount()),
            value(right.stoppedSegmentCount()),
            STOPPED_SEGMENT_DELTA_COUNT,
            ">=2",
            left,
            right);
        addMetricDiff(
            facts,
            "stopped-duration",
            "stoppedDurationSeconds",
            value(left.stoppedDurationSeconds()),
            value(right.stoppedDurationSeconds()),
            STOPPED_DURATION_DELTA_SECONDS,
            ">=600s",
            left,
            right);
        addMetricDiff(
            facts,
            "marker-count",
            "markerCount",
            value(left.markerCount()),
            value(right.markerCount()),
            MARKER_DELTA_COUNT,
            ">=2",
            left,
            right);
        addMetricDiff(
            facts,
            "handover-memo-count",
            "handoverMemoCount",
            value(left.handoverMemoCount()),
            value(right.handoverMemoCount()),
            HANDOVER_MEMO_DELTA_COUNT,
            ">=2",
            left,
            right);
      }
    }
    return List.copyOf(facts);
  }

  private void addDistanceDiff(
      List<OpComparisonDiffFact> facts,
      OpComparisonOperationalPeriodMetrics left,
      OpComparisonOperationalPeriodMetrics right) {
    BigDecimal leftValue = value(left.pathDistanceMeters());
    BigDecimal rightValue = value(right.pathDistanceMeters());
    BigDecimal delta = delta(leftValue, rightValue);
    if (delta.compareTo(DISTANCE_DELTA_METERS) < 0
        || percentDelta(leftValue, rightValue, delta).compareTo(DISTANCE_DELTA_PERCENT) < 0) {
      return;
    }
    facts.add(
        diffFact(
            "path-distance",
            OpComparisonDiffFactType.METRIC_DIFF,
            "pathDistanceMeters",
            leftValue,
            rightValue,
            delta,
            ">=300m && >=15%",
            left,
            right));
  }

  private void addMetricDiff(
      List<OpComparisonDiffFact> facts,
      String factIdPrefix,
      String metricKey,
      BigDecimal leftValue,
      BigDecimal rightValue,
      BigDecimal minimumDelta,
      String threshold,
      OpComparisonOperationalPeriodMetrics left,
      OpComparisonOperationalPeriodMetrics right) {
    BigDecimal delta = delta(leftValue, rightValue);
    if (delta.compareTo(minimumDelta) < 0) {
      return;
    }
    facts.add(
        diffFact(
            factIdPrefix,
            OpComparisonDiffFactType.METRIC_DIFF,
            metricKey,
            leftValue,
            rightValue,
            delta,
            threshold,
            left,
            right));
  }

  private OpComparisonDiffFact diffFact(
      String factIdPrefix,
      OpComparisonDiffFactType type,
      String metricKey,
      BigDecimal leftValue,
      BigDecimal rightValue,
      BigDecimal delta,
      String threshold,
      OpComparisonOperationalPeriodMetrics left,
      OpComparisonOperationalPeriodMetrics right) {
    return new OpComparisonDiffFact(
        "%s-op%d-op%d".formatted(factIdPrefix, left.sequenceNumber(), right.sequenceNumber()),
        type,
        metricKey,
        left.operationalPeriodId(),
        right.operationalPeriodId(),
        leftValue,
        rightValue,
        delta,
        threshold);
  }

  private boolean passesRegionAreaThreshold(OpComparisonRegionFact fact) {
    return fact.areaSquareMeters() != null
        && fact.areaSquareMeters().compareTo(REGION_AREA_SQUARE_METERS) >= 0;
  }

  private List<OpComparisonDiffFact> regionTimeDiffFacts(
      List<OpComparisonRegionFact> regionFacts) {
    List<OpComparisonDiffFact> facts = new ArrayList<>();
    for (OpComparisonRegionFact regionFact : regionFacts) {
      if (regionFact.type() != OpComparisonRegionFactType.COMMON_REGION
          || regionFact.occupancies().size() < 2) {
        continue;
      }
      List<OpComparisonRegionOccupancy> occupancies = orderedOccupancies(regionFact);
      Instant baseline =
          occupancies.stream()
              .map(OpComparisonRegionOccupancy::firstObservedAt)
              .min(Comparator.naturalOrder())
              .orElse(null);
      if (baseline == null) {
        continue;
      }
      for (int i = 0; i < occupancies.size(); i++) {
        for (int j = i + 1; j < occupancies.size(); j++) {
          addRegionTimeDiff(facts, regionFact, baseline, occupancies.get(i), occupancies.get(j));
        }
      }
    }
    return List.copyOf(facts);
  }

  private List<OpComparisonRegionOccupancy> orderedOccupancies(OpComparisonRegionFact regionFact) {
    Map<java.util.UUID, Integer> order = new HashMap<>();
    for (int i = 0; i < regionFact.operationalPeriodIds().size(); i++) {
      order.put(regionFact.operationalPeriodIds().get(i), i);
    }
    return regionFact.occupancies().stream()
        .filter(occupancy -> occupancy.firstObservedAt() != null)
        .sorted(
            Comparator.comparingInt(
                occupancy -> order.getOrDefault(occupancy.operationalPeriodId(), Integer.MAX_VALUE)))
        .toList();
  }

  private void addRegionTimeDiff(
      List<OpComparisonDiffFact> facts,
      OpComparisonRegionFact regionFact,
      Instant baseline,
      OpComparisonRegionOccupancy left,
      OpComparisonRegionOccupancy right) {
    BigDecimal leftValue = value(Duration.between(baseline, left.firstObservedAt()).getSeconds());
    BigDecimal rightValue = value(Duration.between(baseline, right.firstObservedAt()).getSeconds());
    BigDecimal delta = delta(leftValue, rightValue);
    if (delta.compareTo(REGION_TIME_DELTA_SECONDS) < 0) {
      return;
    }
    facts.add(
        new OpComparisonDiffFact(
            "region-time-%s-op%d-op%d"
                .formatted(regionFact.factId(), opOrder(regionFact, left), opOrder(regionFact, right)),
            OpComparisonDiffFactType.REGION_TIME_DIFF,
            "firstObservedDelaySeconds",
            left.operationalPeriodId(),
            right.operationalPeriodId(),
            leftValue,
            rightValue,
            delta,
            ">=1800s"));
  }

  private int opOrder(OpComparisonRegionFact regionFact, OpComparisonRegionOccupancy occupancy) {
    int index = regionFact.operationalPeriodIds().indexOf(occupancy.operationalPeriodId());
    return index < 0 ? 0 : index + 1;
  }

  private BigDecimal percentDelta(BigDecimal left, BigDecimal right, BigDecimal delta) {
    BigDecimal denominator = left.abs().max(right.abs());
    if (denominator.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return delta.multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP);
  }

  private static BigDecimal delta(BigDecimal left, BigDecimal right) {
    return left.subtract(right).abs();
  }

  private static BigDecimal value(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private static BigDecimal value(long value) {
    return BigDecimal.valueOf(value);
  }

  private static BigDecimal value(int value) {
    return BigDecimal.valueOf(value);
  }
}
