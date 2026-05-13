package com.surimap.path.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class GpsPathValidationFixturesTest {

  @Test
  void normalFixtureUsesHarnessPointAndSegmentIds() {
    assertThat(GpsPathValidationFixtures.NORMAL_PATH_ALIAS).isEqualTo("path-precinct-mixed-001");
    assertThat(GpsPathValidationFixtures.NORMAL_POLICE_PHONE_CODE).isEqualTo("dev-precinct-car-01");
    assertThat(GpsPathValidationFixtures.NORMAL_POINTS)
        .extracting(GpsPathValidationFixtures.GpsPointFixture::pointId)
        .containsExactly(
            "gps-precinct-001",
            "gps-precinct-002",
            "gps-precinct-003",
            "gps-precinct-004",
            "gps-precinct-005",
            "gps-precinct-006",
            "gps-precinct-007",
            "gps-precinct-008");
    assertThat(GpsPathValidationFixtures.NORMAL_SEGMENTS)
        .extracting(GpsPathValidationFixtures.PathSegmentFixture::type)
        .containsExactly("VEHICLE", "FOOT");
    assertThat(GpsPathValidationFixtures.NORMAL_SEGMENTS)
        .extracting(GpsPathValidationFixtures.PathSegmentFixture::startIndex)
        .containsExactly(0, 4);
    assertThat(GpsPathValidationFixtures.NORMAL_SEGMENTS)
        .extracting(GpsPathValidationFixtures.PathSegmentFixture::endIndex)
        .containsExactly(3, 7);
  }

  @Test
  void structuralFixturesFreezeInvalidGeometryCasesBeforeValidatorImplementation() {
    assertThat(GpsPathValidationFixtures.STRUCTURAL_FAILURE_FIXTURES)
        .extracting(GpsPathValidationFixtures.StructuralFailureFixture::name)
        .containsExactly(
            "gps-batch-over-limit-121",
            "coord-outside-envelope",
            "coord-latlon-swapped",
            "gps-client-ts-non-monotonic",
            "point-null-nan",
            "precision-over-6dp");
    assertThat(GpsPathValidationFixtures.STRUCTURAL_FAILURE_FIXTURES)
        .extracting(GpsPathValidationFixtures.StructuralFailureFixture::publicError)
        .containsOnly("invalid_geometry");
    assertThat(GpsPathValidationFixtures.BATCH_LIMIT_EXCEEDED.points())
        .hasSize(GpsPathValidationCriteria.MAX_POINTS_PER_BATCH + 1);
    assertThat(GpsPathValidationFixtures.BATCH_LIMIT_EXCEEDED.points())
        .extracting(point -> canonicalCoordinateKey(point.lon(), point.lat()))
        .doesNotHaveDuplicates();
    assertThat(GpsPathValidationFixtures.BATCH_LIMIT_EXCEEDED.points())
        .isSortedAccordingTo(
            Comparator.comparing(GpsPathValidationFixtures.GpsPointFixture::clientTs));
    assertThat(GpsPathValidationFixtures.BATCH_LIMIT_EXCEEDED.points())
        .allSatisfy(
            point -> {
              assertThat(point.lon())
                  .isBetween(
                      BigDecimal.valueOf(GpsPathValidationCriteria.HARNESS_ENVELOPE.minLon()),
                      BigDecimal.valueOf(GpsPathValidationCriteria.HARNESS_ENVELOPE.maxLon()));
              assertThat(point.lat())
                  .isBetween(
                      BigDecimal.valueOf(GpsPathValidationCriteria.HARNESS_ENVELOPE.minLat()),
                      BigDecimal.valueOf(GpsPathValidationCriteria.HARNESS_ENVELOPE.maxLat()));
              assertThat(point.lon().scale())
                  .isLessThanOrEqualTo(GpsPathValidationCriteria.CANONICAL_COORDINATE_SCALE);
              assertThat(point.lat().scale())
                  .isLessThanOrEqualTo(GpsPathValidationCriteria.CANONICAL_COORDINATE_SCALE);
              assertThat(point.speedMps())
                  .isBetween(
                      BigDecimal.ZERO,
                      BigDecimal.valueOf(GpsPathValidationCriteria.MAX_SPEED_METERS_PER_SECOND));
              assertThat(point.horizontalAccuracyM())
                  .isLessThanOrEqualTo(GpsPathValidationCriteria.MAX_HORIZONTAL_ACCURACY_METERS);
            });
    assertThat(GpsPathValidationFixtures.COORDINATE_OUTSIDE_ENVELOPE.points().get(0).lon())
        .isGreaterThan(BigDecimal.valueOf(GpsPathValidationCriteria.HARNESS_ENVELOPE.maxLon()));
    assertThat(GpsPathValidationFixtures.COORDINATE_LAT_LON_SWAPPED.points().get(0).lat())
        .isGreaterThan(BigDecimal.valueOf(90));
    assertThat(GpsPathValidationFixtures.NON_MONOTONIC_CLIENT_TS.points().get(1).clientTs())
        .isBefore(GpsPathValidationFixtures.NON_MONOTONIC_CLIENT_TS.points().get(0).clientTs());
    assertThat(GpsPathValidationFixtures.NULL_COORDINATE.points().get(0).lon()).isNull();
    assertThat(GpsPathValidationFixtures.PRECISION_OVER_SIX_DP.points().get(0).lon().scale())
        .isGreaterThan(GpsPathValidationCriteria.CANONICAL_COORDINATE_SCALE);
  }

  @Test
  void qualityFixturesFreezeExcludedPointCriteriaBeforeValidatorImplementation() {
    assertThat(GpsPathValidationFixtures.QUALITY_FAILURE_FIXTURES)
        .extracting(GpsPathValidationFixtures.QualityFailureFixture::name)
        .containsExactly(
            "gps-low-quality-accuracy-001",
            "gps-low-quality-skew-001",
            "gps-low-quality-speed-negative-001",
            "gps-low-quality-speed-over-001",
            "gps-low-quality-jump-001");
    assertThat(GpsPathValidationFixtures.QUALITY_FAILURE_FIXTURES)
        .extracting(fixture -> fixture.expectedReason().name())
        .containsExactly(
            "LOW_ACCURACY", "CLOCK_SKEW", "INVALID_SPEED", "INVALID_SPEED", "DISTANCE_JUMP");
    assertThat(GpsPathValidationFixtures.QUALITY_FAILURE_FIXTURES)
        .allSatisfy(
            fixture -> assertThat(fixture.expectedDisposition()).isEqualTo("excludedPoints"));
    assertThat(GpsPathValidationFixtures.QUALITY_FAILURE_FIXTURES)
        .filteredOn(
            fixture ->
                fixture.violationDirection()
                    == GpsPathValidationFixtures.ViolationDirection.BELOW_MIN)
        .allSatisfy(fixture -> assertThat(fixture.measuredValue()).isLessThan(fixture.threshold()));
    assertThat(GpsPathValidationFixtures.QUALITY_FAILURE_FIXTURES)
        .filteredOn(
            fixture ->
                fixture.violationDirection()
                    == GpsPathValidationFixtures.ViolationDirection.ABOVE_MAX)
        .allSatisfy(
            fixture -> assertThat(fixture.measuredValue()).isGreaterThan(fixture.threshold()));
  }

  @Test
  void speedQualityFixturesFreezeBothInvalidSpeedBounds() {
    assertThat(GpsPathValidationFixtures.QUALITY_FAILURE_FIXTURES)
        .filteredOn(fixture -> fixture.rule().contains("speedMps"))
        .extracting(GpsPathValidationFixtures.QualityFailureFixture::point)
        .extracting(GpsPathValidationFixtures.GpsPointFixture::speedMps)
        .containsExactly(new BigDecimal("-1.0"), new BigDecimal("46.0"));
  }

  @Test
  void timestampSkewFixtureFreezesDerivableClockSkewInputs() {
    var fixture = GpsPathValidationFixtures.TIMESTAMP_SKEW;
    assertThat(fixture.points())
        .singleElement()
        .satisfies(
            point -> {
              var skewSeconds =
                  Math.abs(
                      Duration.between(fixture.serverReceivedAt(), point.clientTs()).getSeconds());

              assertThat(BigDecimal.valueOf(skewSeconds))
                  .isEqualByComparingTo(fixture.measuredValue());
            });
    assertThat(fixture.expectedReason())
        .isEqualTo(GpsPathValidationFixtures.QualityFailureReason.CLOCK_SKEW);
  }

  @Test
  void distanceJumpFixtureFreezesDerivableFiveSecondSamplePair() {
    var fixture = GpsPathValidationFixtures.DISTANCE_JUMP;
    assertThat(fixture.points()).hasSize(2);
    assertThat(
            Duration.between(
                fixture.points().get(0).clientTs(), fixture.points().get(1).clientTs()))
        .isEqualTo(Duration.ofSeconds(5));

    var derivedDistanceMeters = distanceMeters(fixture.points().get(0), fixture.points().get(1));
    assertThat(derivedDistanceMeters)
        .isGreaterThan(GpsPathValidationCriteria.MAX_DISTANCE_JUMP_METERS_PER_FIVE_SECONDS);
    assertThat(BigDecimal.valueOf(Math.round(derivedDistanceMeters)))
        .isEqualByComparingTo(fixture.measuredValue());
    assertThat(fixture.expectedReason())
        .isEqualTo(GpsPathValidationFixtures.QualityFailureReason.DISTANCE_JUMP);
  }

  @Test
  void qualityReasonEnumMatchesSearchPathBatchResponseContract() {
    assertThat(GpsPathValidationFixtures.QualityFailureReason.values())
        .extracting(reason -> reason.name())
        .containsExactly("LOW_ACCURACY", "CLOCK_SKEW", "INVALID_SPEED", "DISTANCE_JUMP");
  }

  private static String canonicalCoordinateKey(BigDecimal lon, BigDecimal lat) {
    return lon.setScale(GpsPathValidationCriteria.CANONICAL_COORDINATE_SCALE).toPlainString()
        + ","
        + lat.setScale(GpsPathValidationCriteria.CANONICAL_COORDINATE_SCALE).toPlainString();
  }

  private static double distanceMeters(
      GpsPathValidationFixtures.GpsPointFixture previous,
      GpsPathValidationFixtures.GpsPointFixture current) {
    double earthRadiusMeters = 6_371_000.0;
    double previousLat = Math.toRadians(previous.lat().doubleValue());
    double currentLat = Math.toRadians(current.lat().doubleValue());
    double deltaLat = Math.toRadians(current.lat().subtract(previous.lat()).doubleValue());
    double deltaLon = Math.toRadians(current.lon().subtract(previous.lon()).doubleValue());
    double haversine =
        Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
            + Math.cos(previousLat)
                * Math.cos(currentLat)
                * Math.sin(deltaLon / 2)
                * Math.sin(deltaLon / 2);

    return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
  }
}
