package com.surimap.opcomparison;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpComparisonEvidenceBuilderTest {

  private static final UUID COMPARISON_ID =
      UUID.fromString("99000000-0000-0000-0000-000000000101");
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP1_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID OP2_ID = UUID.fromString("88888888-8888-8888-8888-888888880002");
  private static final Instant BASE_TIME = Instant.parse("2026-05-18T00:00:00Z");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  private final OpComparisonEvidenceBuilder builder = new OpComparisonEvidenceBuilder();

  @Test
  void buildsCoordinateFreeEvidencePackageFromThresholdResult() throws Exception {
    OpComparisonDiffFact distanceDiff =
        new OpComparisonDiffFact(
            "path-distance-op1-op2",
            OpComparisonDiffFactType.METRIC_DIFF,
            "pathDistanceMeters",
            OP1_ID,
            OP2_ID,
            new BigDecimal("1000"),
            new BigDecimal("1400"),
            new BigDecimal("400"),
            ">=300m && >=15%");
    OpComparisonRegionFact region =
        new OpComparisonRegionFact(
            "common-region-001",
            OpComparisonRegionFactType.COMMON_REGION,
            List.of(OP1_ID, OP2_ID),
            """
            {"type":"Polygon","coordinates":[[[126.9000,35.1600],[126.9010,35.1600],[126.9000,35.1600]]]}
            """
                .trim(),
            new BigDecimal("75.0"),
            List.of(occupancy(OP1_ID, 0, 600), occupancy(OP2_ID, 2_400, 3_000)));
    OpComparisonThresholdResult thresholdResult =
        new OpComparisonThresholdResult(
            List.of(distanceDiff), List.of(region), OpComparisonNarrativeStatus.GENERATING);

    OpComparisonEvidencePackage evidence =
        builder.build(COMPARISON_ID, INCIDENT_ID, List.of(metrics(OP1_ID, 1), metrics(OP2_ID, 2)), thresholdResult);

    assertThat(evidence.comparisonId()).isEqualTo(COMPARISON_ID);
    assertThat(evidence.incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(evidence.operationalPeriods()).hasSize(2);
    assertThat(evidence.operationalPeriods().get(0).metrics().pathDistanceMeters()).isEqualTo(1_000L);
    assertThat(evidence.diffFacts()).containsExactly(distanceDiff);
    assertThat(evidence.regionFacts()).hasSize(1);

    OpComparisonRegionEvidence regionEvidence = evidence.regionFacts().get(0);
    assertThat(regionEvidence.factId()).isEqualTo("common-region-001");
    assertThat(regionEvidence.type()).isEqualTo(OpComparisonRegionFactType.COMMON_REGION);
    assertThat(regionEvidence.operationalPeriodIds()).containsExactly(OP1_ID, OP2_ID);
    assertThat(regionEvidence.areaSquareMeters()).isEqualByComparingTo(new BigDecimal("75.0"));
    assertThat(regionEvidence.firstPassTimes())
        .containsEntry(OP1_ID, BASE_TIME)
        .containsEntry(OP2_ID, BASE_TIME.plusSeconds(2_400));
    assertThat(regionEvidence.durationSeconds()).containsEntry(OP1_ID, 600L).containsEntry(OP2_ID, 600L);

    String json = OBJECT_MAPPER.writeValueAsString(evidence);
    assertThat(json).doesNotContain("geometryGeojson", "geometry", "coordinates", "126.9000", "35.1600");
    assertThat(json).doesNotContain("accountId", "policePhone", "policePhoneId");
  }

  @Test
  void keepsEvidenceFactsEmptyWhenThresholdResultSkippedNarrative() {
    OpComparisonEvidencePackage evidence =
        builder.build(
            COMPARISON_ID,
            INCIDENT_ID,
            List.of(metrics(OP1_ID, 1)),
            new OpComparisonThresholdResult(
                List.of(), List.of(), OpComparisonNarrativeStatus.SKIPPED));

    assertThat(evidence.operationalPeriods()).hasSize(1);
    assertThat(evidence.diffFacts()).isEmpty();
    assertThat(evidence.regionFacts()).isEmpty();
  }

  private static OpComparisonOperationalPeriodMetrics metrics(UUID opId, int sequenceNumber) {
    return new OpComparisonOperationalPeriodMetrics(
        opId,
        sequenceNumber,
        BASE_TIME,
        BASE_TIME.plusSeconds(3_600),
        sequenceNumber * 1_000L,
        sequenceNumber * 200L,
        sequenceNumber * 800L,
        sequenceNumber * 20,
        new BigDecimal("%d.0".formatted(sequenceNumber + 3)),
        sequenceNumber,
        sequenceNumber * 300L,
        sequenceNumber,
        sequenceNumber - 1);
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
