package com.surimap.opcomparison;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpComparisonThresholdFilterTest {

  private static final UUID OP1_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID OP2_ID = UUID.fromString("88888888-8888-8888-8888-888888880002");
  private static final Instant BASE_TIME = Instant.parse("2026-05-18T00:00:00Z");

  private final OpComparisonThresholdFilter filter = new OpComparisonThresholdFilter();

  @Test
  void keepsOnlyMetricDiffsThatPassThresholds() {
    OpComparisonThresholdResult result =
        filter.filter(
            List.of(
                metrics(OP1_ID, 1, 1_000L, 10, "4.0", 1, 120L, 1, 0),
                metrics(OP2_ID, 2, 1_400L, 35, "5.2", 4, 900L, 4, 2)),
            List.of());

    assertThat(result.narrativeStatus()).isEqualTo(OpComparisonNarrativeStatus.GENERATING);
    assertThat(result.diffFacts())
        .extracting(OpComparisonDiffFact::metricKey)
        .containsExactly(
            "pathDistanceMeters",
            "walkingRatioPercent",
            "averageSpeedKmh",
            "stoppedSegmentCount",
            "stoppedDurationSeconds",
            "markerCount",
            "handoverMemoCount");

    OpComparisonDiffFact distance = result.diffFacts().get(0);
    assertThat(distance.factId()).isEqualTo("path-distance-op1-op2");
    assertThat(distance.type()).isEqualTo(OpComparisonDiffFactType.METRIC_DIFF);
    assertThat(distance.leftOperationalPeriodId()).isEqualTo(OP1_ID);
    assertThat(distance.rightOperationalPeriodId()).isEqualTo(OP2_ID);
    assertThat(distance.leftValue()).isEqualByComparingTo(new BigDecimal("1000"));
    assertThat(distance.rightValue()).isEqualByComparingTo(new BigDecimal("1400"));
    assertThat(distance.delta()).isEqualByComparingTo(new BigDecimal("400"));
    assertThat(distance.threshold()).isEqualTo(">=300m && >=15%");
  }

  @Test
  void skipsNarrativeWhenAllMetricsAndRegionsAreBelowThresholds() {
    OpComparisonThresholdResult result =
        filter.filter(
            List.of(
                metrics(OP1_ID, 1, 1_000L, 40, "4.0", 2, 600L, 2, 2),
                metrics(OP2_ID, 2, 1_120L, 55, "4.8", 3, 1_000L, 3, 3)),
            List.of(
                region(
                    "common-region-small",
                    OpComparisonRegionFactType.COMMON_REGION,
                    List.of(OP1_ID, OP2_ID),
                    "49.9",
                    List.of(
                        occupancy(OP1_ID, 0, 600),
                        occupancy(OP2_ID, 300, 900)))));

    assertThat(result.diffFacts()).isEmpty();
    assertThat(result.regionFacts()).isEmpty();
    assertThat(result.narrativeStatus()).isEqualTo(OpComparisonNarrativeStatus.SKIPPED);
  }

  @Test
  void keepsRegionFactsByAreaAndAddsCommonRegionTimeDiff() {
    OpComparisonRegionFact common =
        region(
            "common-region-001",
            OpComparisonRegionFactType.COMMON_REGION,
            List.of(OP1_ID, OP2_ID),
            "75.0",
            List.of(occupancy(OP1_ID, 0, 600), occupancy(OP2_ID, 2_400, 3_000)));
    OpComparisonRegionFact different =
        region(
            "different-region-001",
            OpComparisonRegionFactType.DIFFERENT_REGION,
            List.of(OP1_ID),
            "55.0",
            List.of(occupancy(OP1_ID, 900, 1_200)));
    OpComparisonRegionFact small =
        region(
            "different-region-small",
            OpComparisonRegionFactType.DIFFERENT_REGION,
            List.of(OP2_ID),
            "20.0",
            List.of(occupancy(OP2_ID, 900, 1_200)));

    OpComparisonThresholdResult result =
        filter.filter(List.of(), List.of(common, different, small));

    assertThat(result.narrativeStatus()).isEqualTo(OpComparisonNarrativeStatus.GENERATING);
    assertThat(result.regionFacts()).containsExactly(common, different);
    assertThat(result.diffFacts()).hasSize(1);

    OpComparisonDiffFact timeDiff = result.diffFacts().get(0);
    assertThat(timeDiff.type()).isEqualTo(OpComparisonDiffFactType.REGION_TIME_DIFF);
    assertThat(timeDiff.factId()).isEqualTo("region-time-common-region-001-op1-op2");
    assertThat(timeDiff.metricKey()).isEqualTo("firstObservedDelaySeconds");
    assertThat(timeDiff.leftValue()).isEqualByComparingTo(new BigDecimal("0"));
    assertThat(timeDiff.rightValue()).isEqualByComparingTo(new BigDecimal("2400"));
    assertThat(timeDiff.delta()).isEqualByComparingTo(new BigDecimal("2400"));
    assertThat(timeDiff.threshold()).isEqualTo(">=1800s");
  }

  private static OpComparisonOperationalPeriodMetrics metrics(
      UUID opId,
      int sequenceNumber,
      long pathDistanceMeters,
      int walkingRatioPercent,
      String averageSpeedKmh,
      int stoppedSegmentCount,
      long stoppedDurationSeconds,
      int markerCount,
      int handoverMemoCount) {
    return new OpComparisonOperationalPeriodMetrics(
        opId,
        sequenceNumber,
        BASE_TIME,
        BASE_TIME.plusSeconds(3_600),
        pathDistanceMeters,
        0L,
        0L,
        walkingRatioPercent,
        new BigDecimal(averageSpeedKmh),
        stoppedSegmentCount,
        stoppedDurationSeconds,
        markerCount,
        handoverMemoCount);
  }

  private static OpComparisonRegionFact region(
      String factId,
      OpComparisonRegionFactType type,
      List<UUID> opIds,
      String areaSquareMeters,
      List<OpComparisonRegionOccupancy> occupancies) {
    return new OpComparisonRegionFact(
        factId, type, opIds, "{}", new BigDecimal(areaSquareMeters), occupancies);
  }

  private static OpComparisonRegionOccupancy occupancy(
      UUID opId, long firstObservedOffsetSeconds, long lastObservedOffsetSeconds) {
    return new OpComparisonRegionOccupancy(
        opId,
        BASE_TIME.plusSeconds(firstObservedOffsetSeconds),
        BASE_TIME.plusSeconds(lastObservedOffsetSeconds),
        lastObservedOffsetSeconds - firstObservedOffsetSeconds);
  }
}
