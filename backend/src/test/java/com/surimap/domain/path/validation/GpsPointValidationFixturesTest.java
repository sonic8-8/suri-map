package com.surimap.domain.path.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class GpsPointValidationFixturesTest {

  @Test
  void normalFixtureUsesHarnessPointAndSegmentIds() {
    assertThat(GpsPointValidationFixtures.NORMAL_PATH_ALIAS).isEqualTo("path-precinct-mixed-001");
    assertThat(GpsPointValidationFixtures.NORMAL_POLICE_PHONE_CODE).isEqualTo("dev-precinct-car-01");
    assertThat(GpsPointValidationFixtures.NORMAL_POINTS)
        .extracting(GpsPointValidationFixtures.GpsPointFixture::pointId)
        .containsExactly(
            "gps-precinct-001",
            "gps-precinct-002",
            "gps-precinct-003",
            "gps-precinct-004",
            "gps-precinct-005",
            "gps-precinct-006",
            "gps-precinct-007",
            "gps-precinct-008");
    assertThat(GpsPointValidationFixtures.NORMAL_SEGMENTS)
        .extracting(GpsPointValidationFixtures.PathSegmentFixture::type)
        .containsExactly("VEHICLE", "FOOT");
    assertThat(GpsPointValidationFixtures.NORMAL_SEGMENTS)
        .extracting(GpsPointValidationFixtures.PathSegmentFixture::startIndex)
        .containsExactly(0, 4);
    assertThat(GpsPointValidationFixtures.NORMAL_SEGMENTS)
        .extracting(GpsPointValidationFixtures.PathSegmentFixture::endIndex)
        .containsExactly(3, 7);
  }

  @Test
  void structuralFixturesFreezeInvalidGeometryCasesBeforeValidatorImplementation() {
    assertThat(GpsPointValidationFixtures.STRUCTURAL_FAILURE_FIXTURES)
        .extracting(GpsPointValidationFixtures.StructuralFailureFixture::name)
        .containsExactly(
            "gps-batch-over-limit-121",
            "coord-latlon-swapped",
            "gps-client-ts-non-monotonic",
            "point-null-nan",
            "precision-over-6dp");
    assertThat(GpsPointValidationFixtures.STRUCTURAL_FAILURE_FIXTURES)
        .extracting(GpsPointValidationFixtures.StructuralFailureFixture::publicError)
        .containsOnly("invalid_geometry");
    assertThat(GpsPointValidationFixtures.BATCH_LIMIT_EXCEEDED.points())
        .hasSize(GpsPointValidationCriteria.MAX_POINTS_PER_BATCH + 1);
    assertThat(GpsPointValidationFixtures.BATCH_LIMIT_EXCEEDED.points())
        .extracting(point -> canonicalCoordinateKey(point.lon(), point.lat()))
        .doesNotHaveDuplicates();
    assertThat(GpsPointValidationFixtures.BATCH_LIMIT_EXCEEDED.points())
        .isSortedAccordingTo(
            Comparator.comparing(GpsPointValidationFixtures.GpsPointFixture::clientTs));
    assertThat(GpsPointValidationFixtures.BATCH_LIMIT_EXCEEDED.points())
        .allSatisfy(
            point -> {
              assertThat(point.lon())
                  .isBetween(
                      BigDecimal.valueOf(GpsPointValidationCriteria.HARNESS_ENVELOPE.getMinLon()),
                      BigDecimal.valueOf(GpsPointValidationCriteria.HARNESS_ENVELOPE.getMaxLon()));
              assertThat(point.lat())
                  .isBetween(
                      BigDecimal.valueOf(GpsPointValidationCriteria.HARNESS_ENVELOPE.getMinLat()),
                      BigDecimal.valueOf(GpsPointValidationCriteria.HARNESS_ENVELOPE.getMaxLat()));
              assertThat(point.lon().scale())
                  .isLessThanOrEqualTo(GpsPointValidationCriteria.CANONICAL_COORDINATE_SCALE);
              assertThat(point.lat().scale())
                  .isLessThanOrEqualTo(GpsPointValidationCriteria.CANONICAL_COORDINATE_SCALE);
              assertThat(point.speedMps())
                  .isBetween(
                      BigDecimal.ZERO,
                      BigDecimal.valueOf(GpsPointValidationCriteria.MAX_SPEED_METERS_PER_SECOND));
              assertThat(point.horizontalAccuracyM())
                  .isLessThanOrEqualTo(GpsPointValidationCriteria.MAX_HORIZONTAL_ACCURACY_METERS);
            });
    assertThat(GpsPointValidationFixtures.OUTSIDE_SEARCH_AREA_POINTS.get(0).lon())
        .isGreaterThan(BigDecimal.valueOf(GpsPointValidationCriteria.HARNESS_ENVELOPE.getMaxLon()));
    assertThat(GpsPointValidationFixtures.COORDINATE_LAT_LON_SWAPPED.points().get(0).lat())
        .isGreaterThan(BigDecimal.valueOf(90));
    assertThat(GpsPointValidationFixtures.NON_MONOTONIC_CLIENT_TS.points().get(1).clientTs())
        .isBefore(GpsPointValidationFixtures.NON_MONOTONIC_CLIENT_TS.points().get(0).clientTs());
    assertThat(GpsPointValidationFixtures.NULL_COORDINATE.points().get(0).lon()).isNull();
    assertThat(GpsPointValidationFixtures.PRECISION_OVER_SIX_DP.points().get(0).lon().scale())
        .isGreaterThan(GpsPointValidationCriteria.CANONICAL_COORDINATE_SCALE);
  }

  @Test
  void qualityFixturesFreezeExcludedPointCriteriaBeforeValidatorImplementation() {
    assertThat(GpsPointValidationFixtures.QUALITY_FAILURE_FIXTURES)
        .extracting(GpsPointValidationFixtures.QualityFailureFixture::name)
        .containsExactly(
            "gps-low-quality-accuracy-001",
            "gps-low-quality-skew-001",
            "gps-low-quality-speed-negative-001",
            "gps-low-quality-speed-over-001",
            "gps-low-quality-jump-001");
    assertThat(GpsPointValidationFixtures.QUALITY_FAILURE_FIXTURES)
        .extracting(fixture -> fixture.expectedReason().name())
        .containsExactly(
            "LOW_ACCURACY", "CLOCK_SKEW", "INVALID_SPEED", "INVALID_SPEED", "DISTANCE_JUMP");
    assertThat(GpsPointValidationFixtures.QUALITY_FAILURE_FIXTURES)
        .allSatisfy(
            fixture -> assertThat(fixture.expectedDisposition()).isEqualTo("excludedPoints"));
    assertThat(GpsPointValidationFixtures.QUALITY_FAILURE_FIXTURES)
        .filteredOn(
            fixture ->
                fixture.violationDirection()
                    == GpsPointValidationFixtures.ViolationDirection.BELOW_MIN)
        .allSatisfy(fixture -> assertThat(fixture.measuredValue()).isLessThan(fixture.threshold()));
    assertThat(GpsPointValidationFixtures.QUALITY_FAILURE_FIXTURES)
        .filteredOn(
            fixture ->
                fixture.violationDirection()
                    == GpsPointValidationFixtures.ViolationDirection.ABOVE_MAX)
        .allSatisfy(
            fixture -> assertThat(fixture.measuredValue()).isGreaterThan(fixture.threshold()));
  }

  @Test
  void speedQualityFixturesFreezeBothInvalidSpeedBounds() {
    assertThat(GpsPointValidationFixtures.QUALITY_FAILURE_FIXTURES)
        .filteredOn(fixture -> fixture.rule().contains("speedMps"))
        .extracting(GpsPointValidationFixtures.QualityFailureFixture::point)
        .extracting(GpsPointValidationFixtures.GpsPointFixture::speedMps)
        .containsExactly(new BigDecimal("-1.0"), new BigDecimal("46.0"));
  }

  @Test
  void timestampSkewFixtureFreezesDerivableClockSkewInputs() {
    var fixture = GpsPointValidationFixtures.TIMESTAMP_SKEW;
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
        .isEqualTo(GpsPointValidationFixtures.QualityFailureReason.CLOCK_SKEW);
  }

  @Test
  void distanceJumpFixtureFreezesDerivableFiveSecondSamplePair() {
    var fixture = GpsPointValidationFixtures.DISTANCE_JUMP;
    assertThat(fixture.points()).hasSize(2);
    assertThat(
            Duration.between(
                fixture.points().get(0).clientTs(), fixture.points().get(1).clientTs()))
        .isEqualTo(Duration.ofSeconds(5));

    var derivedDistanceMeters = distanceMeters(fixture.points().get(0), fixture.points().get(1));
    assertThat(derivedDistanceMeters)
        .isGreaterThan(GpsPointValidationCriteria.MAX_DISTANCE_JUMP_METERS_PER_FIVE_SECONDS);
    assertThat(BigDecimal.valueOf(Math.round(derivedDistanceMeters)))
        .isEqualByComparingTo(fixture.measuredValue());
    assertThat(fixture.expectedReason())
        .isEqualTo(GpsPointValidationFixtures.QualityFailureReason.DISTANCE_JUMP);
  }

  @Test
  void qualityReasonEnumMatchesSearchPathBatchResponseContract() {
    assertThat(GpsPointValidationFixtures.QualityFailureReason.values())
        .extracting(reason -> reason.name())
        .containsExactly("LOW_ACCURACY", "CLOCK_SKEW", "INVALID_SPEED", "DISTANCE_JUMP");
  }

  private static String canonicalCoordinateKey(BigDecimal lon, BigDecimal lat) {
    return lon.setScale(GpsPointValidationCriteria.CANONICAL_COORDINATE_SCALE).toPlainString()
        + ","
        + lat.setScale(GpsPointValidationCriteria.CANONICAL_COORDINATE_SCALE).toPlainString();
  }

  private static double distanceMeters(
      GpsPointValidationFixtures.GpsPointFixture previous,
      GpsPointValidationFixtures.GpsPointFixture current) {
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
