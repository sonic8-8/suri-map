package com.surimap.path.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GpsPathValidationFixturesTest {

    @Test
    void normalFixtureUsesHarnessPointAndSegmentIds() {
        assertThat(GpsPathValidationFixtures.NORMAL_PATH_ID).isEqualTo("path-precinct-mixed-001");
        assertThat(GpsPathValidationFixtures.NORMAL_DEVICE_ID).isEqualTo("dev-precinct-car-01");
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
                        "gps-precinct-008"
                );
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
                        "precision-over-6dp"
                );
        assertThat(GpsPathValidationFixtures.STRUCTURAL_FAILURE_FIXTURES)
                .extracting(GpsPathValidationFixtures.StructuralFailureFixture::publicError)
                .containsOnly("invalid_geometry");
        assertThat(GpsPathValidationFixtures.BATCH_LIMIT_EXCEEDED.points())
                .hasSize(GpsPathValidationCriteria.MAX_POINTS_PER_BATCH + 1);
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
                        "gps-low-quality-speed-001",
                        "gps-low-quality-jump-001"
                );
        assertThat(GpsPathValidationFixtures.QUALITY_FAILURE_FIXTURES)
                .allSatisfy(fixture -> assertThat(fixture.measuredValue()).isGreaterThan(fixture.threshold()));
    }
}
