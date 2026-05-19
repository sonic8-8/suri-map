package com.surimap.opcomparison;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpComparisonNarrativeValidatorTest {

  private static final UUID COMPARISON_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID INCIDENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OP_1 = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID OP_2 = UUID.fromString("44444444-4444-4444-4444-444444444444");

  private final OpComparisonNarrativeValidator validator =
      new OpComparisonNarrativeValidator(new ObjectMapper().findAndRegisterModules());

  @Test
  void acceptsNarrativeWhenAllNumbersAndEvidenceMatchInputFacts() {
    String observationsJson =
        """
        {"observations":[{"sentence":"1차와 2차의 이동 거리 차이는 420m입니다.","factIds":["metric-pathDistanceMeters"]}]}
        """
            .trim();

    assertThat(validator.isValid(evidencePackage(), observationsJson)).isTrue();
  }

  @Test
  void rejectsForbiddenPhrases() {
    String observationsJson =
        """
        {"observations":[{"sentence":"2차가 더 나음으로 보이며 다음 차수 추천이 필요합니다.","factIds":["metric-pathDistanceMeters"]}]}
        """
            .trim();

    assertThat(validator.isValid(evidencePackage(), observationsJson)).isFalse();
    assertThat(validator.validate(evidencePackage(), observationsJson).failureReason())
        .isEqualTo(OpComparisonNarrativeResult.FORBIDDEN_PHRASE);
  }

  @Test
  void rejectsNumbersThatDoNotExistInEvidencePackage() {
    String observationsJson =
        """
        {"observations":[{"sentence":"1차와 2차의 이동 거리 차이는 430m입니다.","factIds":["metric-pathDistanceMeters"]}]}
        """
            .trim();

    assertThat(validator.isValid(evidencePackage(), observationsJson)).isFalse();
    assertThat(validator.validate(evidencePackage(), observationsJson).failureReason())
        .isEqualTo(OpComparisonNarrativeResult.VALIDATION_REJECTED);
  }

  @Test
  void rejectsEvidenceWithUnknownFactId() {
    String observationsJson =
        """
        {"observations":[{"sentence":"이동 거리 차이는 420m입니다.","factIds":["unknown-fact"]}]}
        """
            .trim();

    assertThat(validator.isValid(evidencePackage(), observationsJson)).isFalse();
    assertThat(validator.validate(evidencePackage(), observationsJson).failureReason())
        .isEqualTo(OpComparisonNarrativeResult.UNSUPPORTED_FACT_ID);
  }

  @Test
  void rejectsNumbersOutsideCitedFactIds() {
    String observationsJson =
        """
        {"observations":[{"sentence":"2차의 공통 영역 체류 시간은 240초입니다.","factIds":["metric-pathDistanceMeters"]}]}
        """
            .trim();

    assertThat(validator.isValid(evidencePackage(), observationsJson)).isFalse();
    assertThat(validator.validate(evidencePackage(), observationsJson).failureReason())
        .isEqualTo(OpComparisonNarrativeResult.VALIDATION_REJECTED);
  }

  @Test
  void rejectsOutputThatReconstructsEvidenceFields() {
    String observationsJson =
        """
        {"observations":[{"sentence":"이동 거리 차이는 420m입니다.","factIds":["metric-pathDistanceMeters"],"evidence":[{"source":"diffFact","key":"delta","value":"420"}]}]}
        """
            .trim();

    assertThat(validator.isValid(evidencePackage(), observationsJson)).isFalse();
    assertThat(validator.validate(evidencePackage(), observationsJson).failureReason())
        .isEqualTo(OpComparisonNarrativeResult.SCHEMA_INVALID);
  }

  @Test
  void rejectsObservationWithoutEvidence() {
    String observationsJson =
        """
        {"observations":[{"sentence":"이동 거리 차이는 420m입니다.","factIds":[]}]}
        """
            .trim();

    assertThat(validator.isValid(evidencePackage(), observationsJson)).isFalse();
    assertThat(validator.validate(evidencePackage(), observationsJson).failureReason())
        .isEqualTo(OpComparisonNarrativeResult.SCHEMA_INVALID);
  }

  @Test
  void rejectsEmptyObservationOutput() {
    String observationsJson =
        """
        {"observations":[]}
        """
            .trim();

    assertThat(validator.isValid(evidencePackage(), observationsJson)).isFalse();
    assertThat(validator.validate(evidencePackage(), observationsJson).failureReason())
        .isEqualTo(OpComparisonNarrativeResult.EMPTY_OUTPUT);
  }

  @Test
  void acceptsFourDigitNumbersAndCommaSeparatedNumbers() {
    String observationsJson =
        """
        {"observations":[{"sentence":"1차 이동 거리는 1,200m입니다.","factIds":["metric-pathDistanceMeters"]}]}
        """
            .trim();

    assertThat(validator.isValid(evidencePackage(), observationsJson)).isTrue();
  }

  @Test
  void acceptsRegionEvidenceValueMatch() {
    String observationsJson =
        """
        {"observations":[{"sentence":"2차의 공통 영역 체류 시간은 240초입니다.","factIds":["region-common-1"]}]}
        """
            .trim();

    assertThat(validator.isValid(evidencePackage(), observationsJson)).isTrue();
  }

  private OpComparisonEvidencePackage evidencePackage() {
    return new OpComparisonEvidencePackage(
        COMPARISON_ID,
        INCIDENT_ID,
        List.of(
            new OpComparisonOperationalPeriodEvidence(
                OP_1,
                1,
                Instant.parse("2026-05-19T00:00:00Z"),
                Instant.parse("2026-05-19T00:30:00Z"),
                metrics(1200, 700, 500, 58, "3.2", 1, 60, 2, 1)),
            new OpComparisonOperationalPeriodEvidence(
                OP_2,
                2,
                Instant.parse("2026-05-19T01:00:00Z"),
                Instant.parse("2026-05-19T01:30:00Z"),
                metrics(1620, 1000, 620, 62, "3.8", 2, 120, 3, 2))),
        List.of(
            new OpComparisonDiffFact(
                "metric-pathDistanceMeters",
                OpComparisonDiffFactType.METRIC_DIFF,
                "pathDistanceMeters",
                OP_1,
                OP_2,
                BigDecimal.valueOf(1200),
                BigDecimal.valueOf(1620),
                BigDecimal.valueOf(420),
                ">=100")),
        List.of(
            new OpComparisonRegionEvidence(
                "region-common-1",
                OpComparisonRegionFactType.COMMON_REGION,
                List.of(OP_1, OP_2),
                BigDecimal.valueOf(3500),
                Map.of(
                    OP_1, Instant.parse("2026-05-19T00:05:00Z"),
                    OP_2, Instant.parse("2026-05-19T01:10:00Z")),
                Map.of(OP_1, 180L, OP_2, 240L))));
  }

  private OpComparisonMetricsEvidence metrics(
      long pathDistanceMeters,
      long walkingDistanceMeters,
      long drivingDistanceMeters,
      int walkingRatioPercent,
      String averageSpeedKmh,
      int stoppedSegmentCount,
      long stoppedDurationSeconds,
      int markerCount,
      int handoverMemoCount) {
    return new OpComparisonMetricsEvidence(
        pathDistanceMeters,
        walkingDistanceMeters,
        drivingDistanceMeters,
        walkingRatioPercent,
        new BigDecimal(averageSpeedKmh),
        stoppedSegmentCount,
        stoppedDurationSeconds,
        markerCount,
        handoverMemoCount);
  }
}
