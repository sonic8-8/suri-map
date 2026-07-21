package com.surimap.domain.path.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.domain.path.GpsPoint;
import com.surimap.domain.path.validation.GpsPointValidationResult.GpsPointExclusionReason;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class GpsPointValidatorTest {

  private final GpsPointValidator validator = new GpsPointValidator();

  @Test
  void batchMax120초과면_invalid_geometry다() {
    var fixture = GpsPointValidationFixtures.BATCH_LIMIT_EXCEEDED;

    assertThatThrownBy(() -> validator.validate(toPoints(fixture.points()), now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("invalid_geometry")
        .hasMessageContaining("points maxItems=120");
  }

  @Test
  void 하네스지도bbox밖이어도_EPSG좌표면_경로기록을_허용한다() {
    var fixture = GpsPointValidationFixtures.OUTSIDE_SEARCH_AREA_POINTS;

    var result =
        validator.validate(toPoints(fixture), OffsetDateTime.parse("2026-04-28T09:05:05+09:00"));

    assertThat(result.getAcceptedPoints())
        .extracting(GpsPoint::getPointId)
        .containsExactly("gps-outside-001", "gps-outside-002");
    assertThat(result.getExcludedPoints()).isEmpty();
  }

  @Test
  void 좌표순서가_lat_lon이면_invalid_geometry다() {
    var fixture = GpsPointValidationFixtures.COORDINATE_LAT_LON_SWAPPED;

    assertThatThrownBy(() -> validator.validate(toPoints(fixture.points()), now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("lon/lat order must be EPSG:4326");
  }

  @Test
  void clientTs가_뒤로_가도_배치전체를_거부하지_않는다() {
    var fixture = GpsPointValidationFixtures.CLIENT_TS_REVERSAL_POINTS;
    var points =
        List.of(
            pointWithCaptureMetadata(fixture.get(0), "gps", 10_000_000_000L),
            pointWithCaptureMetadata(fixture.get(1), "network", 15_000_000_000L));

    var result =
        validator.validate(points, OffsetDateTime.parse("2026-04-28T09:07:10+09:00"));

    assertThat(result.getAcceptedPoints())
        .extracting(GpsPoint::getPointId)
        .containsExactly("gps-ts-001", "gps-ts-002");
    assertThat(result.getExcludedPoints()).isEmpty();
  }

  @Test
  void elapsedRealtime이_앞선_좌표보다_작으면_해당_좌표만_제외한다() {
    var fixture = GpsPointValidationFixtures.CLIENT_TS_REVERSAL_POINTS;
    var points =
        List.of(
            pointWithCaptureMetadata(fixture.get(0), "gps", 15_000_000_000L),
            pointWithCaptureMetadata(fixture.get(1), "network", 10_000_000_000L));

    var result =
        validator.validate(points, OffsetDateTime.parse("2026-04-28T09:07:10+09:00"));

    assertThat(result.getAcceptedPoints())
        .extracting(GpsPoint::getPointId)
        .containsExactly("gps-ts-001");
    assertThat(result.getExcludedPoints())
        .singleElement()
        .satisfies(
            excluded ->
                assertThat(excluded.getReason()).isEqualTo(GpsPointExclusionReason.OUT_OF_ORDER));
  }

  @Test
  void null좌표면_invalid_geometry다() {
    var fixture = GpsPointValidationFixtures.NULL_COORDINATE;

    assertThatThrownBy(() -> validator.validate(toPoints(fixture.points()), now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("null or NaN coordinate");
  }

  @Test
  void 소수점6자리초과면_invalid_geometry다() {
    var fixture = GpsPointValidationFixtures.PRECISION_OVER_SIX_DP;

    assertThatThrownBy(() -> validator.validate(toPoints(fixture.points()), now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("precision exceeds 6 decimal places");
  }

  @Test
  void accuracy저하면_excludedPoints로_분류한다() {
    var fixture = GpsPointValidationFixtures.LOW_ACCURACY;

    var result =
        validator.validate(
            withValidLeadPoint(toPoints(fixture.points())), fixture.serverReceivedAt());

    assertThat(result.getAcceptedPoints()).hasSize(1);
    assertThat(result.getExcludedPoints())
        .singleElement()
        .satisfies(
            ex -> assertThat(ex.getReason()).isEqualTo(GpsPointExclusionReason.LOW_ACCURACY));
  }

  @Test
  void clockSkew초과면_excludedPoints로_분류한다() {
    var fixture = GpsPointValidationFixtures.TIMESTAMP_SKEW;

    var result =
        validator.validate(
            withValidLeadPoint(toPoints(fixture.points())), fixture.serverReceivedAt());

    assertThat(result.getAcceptedPoints()).hasSize(1);
    assertThat(result.getExcludedPoints())
        .singleElement()
        .satisfies(ex -> assertThat(ex.getReason()).isEqualTo(GpsPointExclusionReason.CLOCK_SKEW));
  }

  @Test
  void speed범위위반이면_excludedPoints로_분류한다() {
    var negative = GpsPointValidationFixtures.NEGATIVE_SPEED;
    var over = GpsPointValidationFixtures.EXCESSIVE_SPEED;

    var negativeResult =
        validator.validate(
            withValidLeadPoint(toPoints(negative.points())), negative.serverReceivedAt());
    var overResult =
        validator.validate(withValidLeadPoint(toPoints(over.points())), over.serverReceivedAt());

    assertThat(negativeResult.getExcludedPoints())
        .singleElement()
        .satisfies(
            ex -> assertThat(ex.getReason()).isEqualTo(GpsPointExclusionReason.INVALID_SPEED));
    assertThat(overResult.getExcludedPoints())
        .singleElement()
        .satisfies(
            ex -> assertThat(ex.getReason()).isEqualTo(GpsPointExclusionReason.INVALID_SPEED));
  }

  @Test
  void jump거리초과면_excludedPoints로_분류한다() {
    var fixture = GpsPointValidationFixtures.DISTANCE_JUMP;

    var result = validator.validate(toPoints(fixture.points()), fixture.serverReceivedAt());

    assertThat(result.getAcceptedPoints()).hasSize(1);
    assertThat(result.getExcludedPoints())
        .singleElement()
        .satisfies(
            ex -> assertThat(ex.getReason()).isEqualTo(GpsPointExclusionReason.DISTANCE_JUMP));
  }

  @Test
  void 포인트가_1개면_invalid_geometry다() {
    var singlePoint = List.of(toPoints(GpsPointValidationFixtures.LOW_ACCURACY.points()).get(0));
    assertThatThrownBy(() -> validator.validate(singlePoint, now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("points minItems=2");
  }

  @Test
  void pointId중복이면_invalid_geometry다() {
    var points = toPoints(GpsPointValidationFixtures.NORMAL_POINTS);
    var duplicated =
        List.of(
            points.get(0),
            GpsPoint.builder()
                .pointId(points.get(0).getPointId())
                .clientTs(points.get(1).getClientTs())
                .lon(points.get(1).getLon())
                .lat(points.get(1).getLat())
                .speedMps(points.get(1).getSpeedMps())
                .horizontalAccuracyM(points.get(1).getHorizontalAccuracyM())
                .build());

    assertThatThrownBy(() -> validator.validate(duplicated, now()))
        .isInstanceOf(InvalidGpsPathBatchException.class)
        .hasMessageContaining("pointId must be unique");
  }

  @Test
  void activeOverallSearchArea밖이어도_EPSG좌표면_경로기록을_허용한다() {
    var activeArea =
        GpsPointValidationCriteria.GeoEnvelope.builder()
            .minLon(126.900000)
            .minLat(35.150000)
            .maxLon(126.905000)
            .maxLat(35.155000)
            .build();

    var result =
        validator.validate(
            toPoints(GpsPointValidationFixtures.NORMAL_POINTS),
            OffsetDateTime.parse("2026-04-28T09:00:20+09:00"),
            activeArea);

    assertThat(result.getAcceptedPoints()).hasSize(8);
    assertThat(result.getExcludedPoints()).isEmpty();
  }

  @Test
  void activeOverallSearchArea없으면_하네스_범위_내_초동_경로는_허용한다() {
    var serverReceivedAt = OffsetDateTime.parse("2026-04-28T09:00:20+09:00");

    var result =
        validator.validate(
            toPoints(GpsPointValidationFixtures.NORMAL_POINTS), serverReceivedAt, null);

    assertThat(result.getAcceptedPoints()).hasSize(8);
    assertThat(result.getExcludedPoints()).isEmpty();
  }

  @Test
  void 정상fixture는_acceptedPoints로_유지된다() {
    var serverReceivedAt = OffsetDateTime.parse("2026-04-28T09:00:20+09:00");

    var result =
        validator.validate(toPoints(GpsPointValidationFixtures.NORMAL_POINTS), serverReceivedAt);

    assertThat(result.getAcceptedPoints()).hasSize(8);
    assertThat(result.getExcludedPoints()).isEmpty();
  }

  @Test
  void lowQualityPoint는_승격되지_않고_제외된다() {
    var fixture = GpsPointValidationFixtures.DISTANCE_JUMP;
    var result = validator.validate(toPoints(fixture.points()), fixture.serverReceivedAt());

    assertThat(result.getAcceptedPoints())
        .extracting(GpsPoint::getPointId)
        .containsExactly("gps-quality-jump-prev-001");
    assertThat(result.getExcludedPoints())
        .extracting(excluded -> excluded.getPoint().getPointId())
        .containsExactly("gps-quality-jump-001");
  }

  private static List<GpsPoint> toPoints(
      List<GpsPointValidationFixtures.GpsPointFixture> fixtures) {
    return fixtures.stream()
        .map(
            point ->
                GpsPoint.builder()
                    .pointId(point.pointId())
                    .clientTs(point.clientTs())
                    .lon(point.lon())
                    .lat(point.lat())
                    .speedMps(point.speedMps())
                    .horizontalAccuracyM(point.horizontalAccuracyM())
                    .build())
        .toList();
  }

  private static GpsPoint pointWithCaptureMetadata(
      GpsPointValidationFixtures.GpsPointFixture point,
      String locationProvider,
      long elapsedRealtimeNanos) {
    return GpsPoint.builder()
        .pointId(point.pointId())
        .clientTs(point.clientTs())
        .lon(point.lon())
        .lat(point.lat())
        .speedMps(point.speedMps())
        .horizontalAccuracyM(point.horizontalAccuracyM())
        .locationProvider(locationProvider)
        .elapsedRealtimeNanos(elapsedRealtimeNanos)
        .build();
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.parse("2026-04-28T09:10:00+09:00");
  }

  private static List<GpsPoint> withValidLeadPoint(List<GpsPoint> points) {
    var first = points.get(0);
    var lead =
        GpsPoint.builder()
            .pointId("gps-valid-lead-001")
            .clientTs(first.getClientTs().minusSeconds(5))
            .lon(first.getLon())
            .lat(first.getLat())
            .speedMps(
                first.getSpeedMps().compareTo(java.math.BigDecimal.ZERO) < 0
                    ? java.math.BigDecimal.ONE
                    : first.getSpeedMps().min(java.math.BigDecimal.valueOf(10)))
            .horizontalAccuracyM(5)
            .build();
    return List.of(lead, first);
  }
}
