package com.surimap.path.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.path.validation.GpsPathValidationResult.QualityReason;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class GpsPathValidatorTest {

  private final GpsPathValidator validator = new GpsPathValidator();

  @Test
  void batchMax120초과면_invalid_geometry다() {
    var fixture = GpsPathValidationFixtures.BATCH_LIMIT_EXCEEDED;

    assertThatThrownBy(() -> validator.validateBatch(toPoints(fixture.points()), now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("invalid_geometry")
        .hasMessageContaining("points maxItems=120");
  }

  @Test
  void bbox이탈이면_invalid_geometry다() {
    var fixture = GpsPathValidationFixtures.COORDINATE_OUTSIDE_ENVELOPE;

    assertThatThrownBy(() -> validator.validateBatch(toPoints(fixture.points()), now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("point outside harness envelope");
  }

  @Test
  void 좌표순서가_lat_lon이면_invalid_geometry다() {
    var fixture = GpsPathValidationFixtures.COORDINATE_LAT_LON_SWAPPED;

    assertThatThrownBy(() -> validator.validateBatch(toPoints(fixture.points()), now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("lon/lat order must be EPSG:4326");
  }

  @Test
  void timestamp역전이면_invalid_geometry다() {
    var fixture = GpsPathValidationFixtures.NON_MONOTONIC_CLIENT_TS;

    assertThatThrownBy(() -> validator.validateBatch(toPoints(fixture.points()), now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("clientTs strict monotonic");
  }

  @Test
  void null좌표면_invalid_geometry다() {
    var fixture = GpsPathValidationFixtures.NULL_COORDINATE;

    assertThatThrownBy(() -> validator.validateBatch(toPoints(fixture.points()), now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("null or NaN coordinate");
  }

  @Test
  void 소수점6자리초과면_invalid_geometry다() {
    var fixture = GpsPathValidationFixtures.PRECISION_OVER_SIX_DP;

    assertThatThrownBy(() -> validator.validateBatch(toPoints(fixture.points()), now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("precision exceeds 6 decimal places");
  }

  @Test
  void accuracy저하면_excludedPoints로_분류한다() {
    var fixture = GpsPathValidationFixtures.LOW_ACCURACY;

    var result =
        validator.validateBatch(withValidLeadPoint(toPoints(fixture.points())), fixture.serverReceivedAt());

    assertThat(result.acceptedPoints()).hasSize(1);
    assertThat(result.excludedPoints()).singleElement().satisfies(ex -> assertThat(ex.reason()).isEqualTo(QualityReason.LOW_ACCURACY));
  }

  @Test
  void clockSkew초과면_excludedPoints로_분류한다() {
    var fixture = GpsPathValidationFixtures.TIMESTAMP_SKEW;

    var result =
        validator.validateBatch(withValidLeadPoint(toPoints(fixture.points())), fixture.serverReceivedAt());

    assertThat(result.acceptedPoints()).hasSize(1);
    assertThat(result.excludedPoints()).singleElement().satisfies(ex -> assertThat(ex.reason()).isEqualTo(QualityReason.CLOCK_SKEW));
  }

  @Test
  void speed범위위반이면_excludedPoints로_분류한다() {
    var negative = GpsPathValidationFixtures.NEGATIVE_SPEED;
    var over = GpsPathValidationFixtures.EXCESSIVE_SPEED;

    var negativeResult =
        validator.validateBatch(withValidLeadPoint(toPoints(negative.points())), negative.serverReceivedAt());
    var overResult =
        validator.validateBatch(withValidLeadPoint(toPoints(over.points())), over.serverReceivedAt());

    assertThat(negativeResult.excludedPoints()).singleElement().satisfies(ex -> assertThat(ex.reason()).isEqualTo(QualityReason.INVALID_SPEED));
    assertThat(overResult.excludedPoints()).singleElement().satisfies(ex -> assertThat(ex.reason()).isEqualTo(QualityReason.INVALID_SPEED));
  }

  @Test
  void jump거리초과면_excludedPoints로_분류한다() {
    var fixture = GpsPathValidationFixtures.DISTANCE_JUMP;

    var result = validator.validateBatch(toPoints(fixture.points()), fixture.serverReceivedAt());

    assertThat(result.acceptedPoints()).hasSize(1);
    assertThat(result.excludedPoints()).singleElement().satisfies(ex -> assertThat(ex.reason()).isEqualTo(QualityReason.DISTANCE_JUMP));
  }

  @Test
  void 포인트가_1개면_invalid_geometry다() {
    var singlePoint = List.of(toPoints(GpsPathValidationFixtures.LOW_ACCURACY.points()).get(0));
    assertThatThrownBy(() -> validator.validateBatch(singlePoint, now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("points minItems=2");
  }

  @Test
  void pointId중복이면_invalid_geometry다() {
    var points = toPoints(GpsPathValidationFixtures.NORMAL_POINTS);
    var duplicated =
        List.of(
            points.get(0),
            new GpsPathPoint(
                points.get(0).pointId(),
                points.get(1).clientTs(),
                points.get(1).lon(),
                points.get(1).lat(),
                points.get(1).speedMps(),
                points.get(1).horizontalAccuracyM()));

    assertThatThrownBy(() -> validator.validateBatch(duplicated, now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("pointId must be unique");
  }

  @Test
  void activeOverallSearchArea밖이면_invalid_geometry다() {
    var activeArea = new GpsPathValidationCriteria.GeoEnvelope(126.955000, 37.569000, 126.957000, 37.570300);

    assertThatThrownBy(
            () ->
                validator.validateBatch(
                    toPoints(GpsPathValidationFixtures.NORMAL_POINTS),
                    OffsetDateTime.parse("2026-04-28T09:00:20+09:00"),
                    activeArea))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("point outside active overall_search_area");
  }

  @Test
  void 정상fixture는_acceptedPoints로_유지된다() {
    var serverReceivedAt = OffsetDateTime.parse("2026-04-28T09:00:20+09:00");

    var result = validator.validateBatch(toPoints(GpsPathValidationFixtures.NORMAL_POINTS), serverReceivedAt);

    assertThat(result.acceptedPoints()).hasSize(8);
    assertThat(result.excludedPoints()).isEmpty();
  }

  @Test
  void lowQualityPoint는_승격되지_않고_제외된다() {
    var fixture = GpsPathValidationFixtures.DISTANCE_JUMP;
    var result = validator.validateBatch(toPoints(fixture.points()), fixture.serverReceivedAt());

    assertThat(result.acceptedPoints())
        .extracting(GpsPathPoint::pointId)
        .containsExactly("gps-quality-jump-prev-001");
    assertThat(result.excludedPoints())
        .extracting(excluded -> excluded.point().pointId())
        .containsExactly("gps-quality-jump-001");
  }

  private static List<GpsPathPoint> toPoints(List<GpsPathValidationFixtures.GpsPointFixture> fixtures) {
    return fixtures.stream()
        .map(
            point ->
                new GpsPathPoint(
                    point.pointId(),
                    point.clientTs(),
                    point.lon(),
                    point.lat(),
                    point.speedMps(),
                    point.horizontalAccuracyM()))
        .toList();
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.parse("2026-04-28T09:10:00+09:00");
  }

  private static List<GpsPathPoint> withValidLeadPoint(List<GpsPathPoint> points) {
    var first = points.get(0);
    var lead =
        new GpsPathPoint(
            "gps-valid-lead-001",
            first.clientTs().minusSeconds(5),
            first.lon(),
            first.lat(),
            first.speedMps().compareTo(java.math.BigDecimal.ZERO) < 0
                ? java.math.BigDecimal.ONE
                : first.speedMps().min(java.math.BigDecimal.valueOf(10)),
            5);
    return List.of(lead, first);
  }
}
