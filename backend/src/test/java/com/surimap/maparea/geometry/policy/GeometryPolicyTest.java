package com.surimap.maparea.geometry.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** GeometryPolicy의 S2 하네스 기준값과 보조 판정 메서드를 검증한다. */
class GeometryPolicyTest {

  /** S2 하네스 기본 도형 정책이다. */
  private final GeometryPolicy policy = GeometryPolicy.s2HarnessDefault();

  @Test
  void s2_하네스_기본값이_스펙과_일치한다() {
    assertThat(policy.srid()).isEqualTo(4326);
    assertThat(policy.crs()).isEqualTo("EPSG:4326");
    assertThat(policy.coordinateOrder()).isEqualTo("[lon, lat]");

    assertThat(policy.minLon()).isEqualByComparingTo("126.647507");
    assertThat(policy.minLat()).isEqualByComparingTo("35.052595");
    assertThat(policy.maxLon()).isEqualByComparingTo("127.017482");
    assertThat(policy.maxLat()).isEqualByComparingTo("35.256837");

    assertThat(policy.coordinatePrecisionDecimals()).isEqualTo(6);
    assertThat(policy.minimumPolygonAreaM2()).isEqualByComparingTo("400");

    assertThat(policy.roundCoordinatesToPrecision()).isTrue();
    assertThat(policy.removeConsecutiveDuplicatePoints()).isTrue();
    assertThat(policy.requireRingClosureAfterNormalization()).isTrue();

    assertThat(policy.shouldCanonicalizeCoordinates()).isTrue();
    assertThat(policy.shouldRemoveConsecutiveDuplicatePoints()).isTrue();
    assertThat(policy.shouldRequireRingClosureAfterNormalization()).isTrue();
  }

  @Test
  void bbox_내부_좌표는_true다() {
    assertThat(policy.containsLonLat(new BigDecimal("126.913400"), new BigDecimal("35.163100")))
        .isTrue();
  }

  @Test
  void bbox_경계값은_true다() {
    assertThat(policy.containsLonLat(new BigDecimal("126.647507"), new BigDecimal("35.052595")))
        .isTrue();

    assertThat(policy.containsLonLat(new BigDecimal("127.017482"), new BigDecimal("35.256837")))
        .isTrue();
  }

  @Test
  void bbox_밖_좌표는_false다() {
    assertThat(policy.containsLonLat(new BigDecimal("127.200000"), new BigDecimal("35.163100")))
        .isFalse();
  }

  @Test
  void 유효하지_않은_경도는_false다() {
    assertThat(policy.containsLonLat(new BigDecimal("181"), new BigDecimal("35.163100"))).isFalse();
  }

  @Test
  void 유효하지_않은_위도는_false다() {
    assertThat(policy.containsLonLat(new BigDecimal("126.913400"), new BigDecimal("91"))).isFalse();
  }

  @Test
  void lat_lon_순서가_뒤집힌_좌표는_bbox_밖으로_판정된다() {
    assertThat(policy.containsLonLat(new BigDecimal("35.163100"), new BigDecimal("126.913400")))
        .isFalse();
  }

  @Test
  void null_좌표는_false다() {
    assertThat(policy.containsLonLat(null, new BigDecimal("35.163100"))).isFalse();
    assertThat(policy.containsLonLat(new BigDecimal("126.913400"), null)).isFalse();
  }

  @Test
  void 소수점_6자리_좌표는_이미_canonical_precision이다() {
    assertThat(policy.isAlreadyCanonicalPrecision(new BigDecimal("126.913400"))).isTrue();
  }

  @Test
  void 끝의_0은_precision_초과로_보지_않는다() {
    assertThat(policy.isAlreadyCanonicalPrecision(new BigDecimal("126.9134000"))).isTrue();
  }

  @Test
  void 소수점_7자리_좌표는_아직_canonical_precision이_아니다() {
    assertThat(policy.isAlreadyCanonicalPrecision(new BigDecimal("126.9134007"))).isFalse();
  }

  @Test
  void canonicalizeCoordinate는_6자리로_반올림한다() {
    assertThat(policy.canonicalizeCoordinate(new BigDecimal("126.9134007")))
        .isEqualByComparingTo("126.913401");
  }

  @Test
  void null_좌표를_canonicalize하면_예외가_발생한다() {
    assertThatThrownBy(() -> policy.canonicalizeCoordinate(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("좌표값은 null일 수 없습니다.");
  }

  @Test
  void S2_정책은_4326이_아닌_SRID를_허용하지_않는다() {
    assertInvalidPolicy(
        3857,
        "EPSG:4326",
        "[lon, lat]",
        6,
        "400",
        true,
        true,
        true,
        "S2 도형 정책의 SRID는 EPSG:4326만 허용합니다.");
  }

  @Test
  void S2_정책은_EPSG4326이_아닌_CRS를_허용하지_않는다() {
    assertInvalidPolicy(
        4326,
        "WGS84",
        "[lon, lat]",
        6,
        "400",
        true,
        true,
        true,
        "S2 도형 정책의 CRS는 EPSG:4326만 허용합니다.");
  }

  @Test
  void S2_정책은_lon_lat이_아닌_좌표순서를_허용하지_않는다() {
    assertInvalidPolicy(
        4326,
        "EPSG:4326",
        "[lat, lon]",
        6,
        "400",
        true,
        true,
        true,
        "S2 GeoJSON 좌표 순서는 [lon, lat]만 허용합니다.");
  }

  @Test
  void S2_정책은_소수점_6자리가_아닌_precision을_허용하지_않는다() {
    assertInvalidPolicy(
        4326,
        "EPSG:4326",
        "[lon, lat]",
        7,
        "400",
        true,
        true,
        true,
        "S2 좌표 canonical precision은 소수점 6자리만 허용합니다.");
  }

  @Test
  void S2_정책은_최소면적_400이_아닌_값을_허용하지_않는다() {
    assertInvalidPolicy(
        4326,
        "EPSG:4326",
        "[lon, lat]",
        6,
        "399",
        true,
        true,
        true,
        "S2 최소 Polygon 면적은 400㎡여야 합니다.");
  }

  @Test
  void S2_정책은_좌표정규화_비활성화를_허용하지_않는다() {
    assertInvalidPolicy(
        4326,
        "EPSG:4326",
        "[lon, lat]",
        6,
        "400",
        false,
        true,
        true,
        "S2 좌표는 소수점 6자리 canonical 값으로 정규화해야 합니다.");
  }

  @Test
  void S2_정책은_연속중복좌표제거_비활성화를_허용하지_않는다() {
    assertInvalidPolicy(
        4326,
        "EPSG:4326",
        "[lon, lat]",
        6,
        "400",
        true,
        false,
        true,
        "S2 Polygon 검증 전 연속 중복 좌표 제거 정책이 필요합니다.");
  }

  @Test
  void S2_정책은_ring_closure_검증_비활성화를_허용하지_않는다() {
    assertInvalidPolicy(
        4326,
        "EPSG:4326",
        "[lon, lat]",
        6,
        "400",
        true,
        true,
        false,
        "S2 Polygon은 정규화 이후 ring closure를 검증해야 합니다.");
  }

  private void assertInvalidPolicy(
      int srid,
      String crs,
      String coordinateOrder,
      int precision,
      String minimumArea,
      boolean round,
      boolean dedupe,
      boolean ringClosure,
      String message) {
    assertThatThrownBy(
            () ->
                new GeometryPolicy(
                    srid,
                    crs,
                    coordinateOrder,
                    new BigDecimal("126.647507"),
                    new BigDecimal("35.052595"),
                    new BigDecimal("127.017482"),
                    new BigDecimal("35.256837"),
                    precision,
                    new BigDecimal(minimumArea),
                    round,
                    dedupe,
                    ringClosure))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(message);
  }
}
