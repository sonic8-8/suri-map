package com.surimap.maparea.geometry.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.maparea.fixture.GeometryFixtures;
import com.surimap.maparea.geometry.exception.InvalidGeometryException;
import com.surimap.maparea.geometry.policy.GeometryPolicy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** GeometryValidator의 S2 좌표/ring 1차 검증을 검증한다. */
class GeometryValidatorTest {

  /** S2 하네스 도형 정책이다. */
  private final GeometryPolicy policy = GeometryPolicy.s2HarnessDefault();

  /** S2 좌표/ring 1차 검증기다. */
  private final GeometryValidator validator = new GeometryValidator(policy);

  @Test
  void point를_6자리_canonical_scale로_정규화한다() {
    List<BigDecimal> point = List.of(new BigDecimal("126.9565"), new BigDecimal("37.5712"));

    List<BigDecimal> result = validator.validateAndCanonicalizePoint(point);

    assertThat(result.get(0)).isEqualByComparingTo("126.956500");
    assertThat(result.get(1)).isEqualByComparingTo("37.571200");
    assertThat(result.get(0).scale()).isEqualTo(6);
    assertThat(result.get(1).scale()).isEqualTo(6);
  }

  @Test
  void 소수점_7자리_좌표는_6자리로_반올림한다() {
    List<BigDecimal> result =
        validator.validateAndCanonicalizePoint(GeometryFixtures.overPrecisionPoint());

    assertThat(result.get(0)).isEqualByComparingTo("126.956501");
    assertThat(result.get(1)).isEqualByComparingTo("37.571201");
    assertThat(result.get(0).scale()).isEqualTo(6);
    assertThat(result.get(1).scale()).isEqualTo(6);
  }

  @Test
  void 반올림_이후에도_bbox_밖이면_invalid_geometry다() {
    List<BigDecimal> point = List.of(new BigDecimal("127.0800005"), new BigDecimal("37.5712000"));

    assertThatThrownBy(() -> validator.validateAndCanonicalizePoint(point))
        .isInstanceOf(InvalidGeometryException.class)
        .hasMessageContaining("좌표가 S2 하네스 bbox 범위를 벗어났습니다.");
  }

  @Test
  void null_point는_invalid_geometry다() {
    assertThatThrownBy(() -> validator.validateAndCanonicalizePoint(null))
        .isInstanceOf(InvalidGeometryException.class)
        .hasMessageContaining("좌표는 [경도, 위도] 형식이어야 합니다.");
  }

  @Test
  void point가_2개_값이_아니면_invalid_geometry다() {
    List<BigDecimal> point = List.of(new BigDecimal("126.956500"));

    assertThatThrownBy(() -> validator.validateAndCanonicalizePoint(point))
        .isInstanceOf(InvalidGeometryException.class)
        .hasMessageContaining("좌표는 [경도, 위도] 형식이어야 합니다.");
  }

  @Test
  void point_내부_좌표값이_null이면_invalid_geometry다() {
    List<BigDecimal> point = new ArrayList<>();
    point.add(new BigDecimal("126.956500"));
    point.add(null);

    assertThatThrownBy(() -> validator.validateAndCanonicalizePoint(point))
        .isInstanceOf(InvalidGeometryException.class)
        .hasMessageContaining("경도와 위도는 null일 수 없습니다.");
  }

  @Test
  void 경도가_EPSG4326_범위를_벗어나면_invalid_geometry다() {
    List<BigDecimal> point = List.of(new BigDecimal("181"), new BigDecimal("37.571200"));

    assertThatThrownBy(() -> validator.validateAndCanonicalizePoint(point))
        .isInstanceOf(InvalidGeometryException.class)
        .hasMessageContaining("좌표가 EPSG:4326 유효 범위를 벗어났습니다.");
  }

  @Test
  void 위도가_EPSG4326_범위를_벗어나면_invalid_geometry다() {
    List<BigDecimal> point = List.of(new BigDecimal("126.956500"), new BigDecimal("91"));

    assertThatThrownBy(() -> validator.validateAndCanonicalizePoint(point))
        .isInstanceOf(InvalidGeometryException.class)
        .hasMessageContaining("좌표가 EPSG:4326 유효 범위를 벗어났습니다.");
  }

  @Test
  void bbox_밖_좌표는_invalid_geometry다() {
    List<BigDecimal> point = List.of(new BigDecimal("127.200000"), new BigDecimal("37.571200"));

    assertThatThrownBy(() -> validator.validateAndCanonicalizePoint(point))
        .isInstanceOf(InvalidGeometryException.class)
        .hasMessageContaining("좌표가 S2 하네스 bbox 범위를 벗어났습니다.");
  }

  @Test
  void lat_lon_순서가_뒤집힌_좌표는_invalid_geometry다() {
    List<BigDecimal> point = List.of(new BigDecimal("37.571200"), new BigDecimal("126.956500"));

    assertThatThrownBy(() -> validator.validateAndCanonicalizePoint(point))
        .isInstanceOf(InvalidGeometryException.class);
  }

  @Test
  void null_ring은_invalid_geometry다() {
    assertThatThrownBy(() -> validator.validateAndCanonicalizePolygonRing(null))
        .isInstanceOf(InvalidGeometryException.class)
        .hasMessageContaining("Polygon ring은 비어 있을 수 없습니다.");
  }

  @Test
  void empty_ring은_invalid_geometry다() {
    assertThatThrownBy(() -> validator.validateAndCanonicalizePolygonRing(List.of()))
        .isInstanceOf(InvalidGeometryException.class)
        .hasMessageContaining("Polygon ring은 비어 있을 수 없습니다.");
  }

  @Test
  void 좌표_수가_4개_미만인_ring은_invalid_geometry다() {
    List<List<BigDecimal>> ring =
        List.of(
            point("126.950000", "37.550000"),
            point("126.960000", "37.550000"),
            point("126.950000", "37.550000"));

    assertThatThrownBy(() -> validator.validateAndCanonicalizePolygonRing(ring))
        .isInstanceOf(InvalidGeometryException.class)
        .hasMessageContaining("Polygon ring은 최소 4개의 좌표가 필요합니다.");
  }

  @Test
  void ring을_6자리_canonical_좌표로_정규화한다() {
    List<List<BigDecimal>> ring =
        List.of(
            point("126.95", "37.55"),
            point("126.96", "37.55"),
            point("126.96", "37.56"),
            point("126.95", "37.55"));

    List<List<BigDecimal>> result = validator.validateAndCanonicalizePolygonRing(ring);

    assertThat(result).hasSize(4);
    assertThat(result.get(0).get(0)).isEqualByComparingTo("126.950000");
    assertThat(result.get(0).get(1)).isEqualByComparingTo("37.550000");
  }

  @Test
  void ring의_연속_중복_좌표를_제거한다() {
    List<List<BigDecimal>> ring =
        List.of(
            point("126.950000", "37.550000"),
            point("126.960000", "37.550000"),
            point("126.960000", "37.550000"),
            point("126.960000", "37.560000"),
            point("126.950000", "37.550000"));

    List<List<BigDecimal>> result = validator.validateAndCanonicalizePolygonRing(ring);

    assertThat(result).hasSize(4);
    assertThat(result.get(0))
        .containsExactly(new BigDecimal("126.950000"), new BigDecimal("37.550000"));
    assertThat(result.get(1))
        .containsExactly(new BigDecimal("126.960000"), new BigDecimal("37.550000"));
    assertThat(result.get(2))
        .containsExactly(new BigDecimal("126.960000"), new BigDecimal("37.560000"));
    assertThat(result.get(3))
        .containsExactly(new BigDecimal("126.950000"), new BigDecimal("37.550000"));
  }

  @Test
  void 정규화_이후_ring이_닫혀있지_않으면_invalid_geometry다() {
    List<List<BigDecimal>> ring =
        List.of(
            point("126.950000", "37.550000"),
            point("126.960000", "37.550000"),
            point("126.960000", "37.560000"),
            point("126.951000", "37.551000"));

    assertThatThrownBy(() -> validator.validateAndCanonicalizePolygonRing(ring))
        .isInstanceOf(InvalidGeometryException.class)
        .hasMessageContaining("Polygon ring은 정규화 이후 첫 좌표와 마지막 좌표가 같아야 합니다.");
  }

  @Test
  void 정규화_이후_첫좌표와_마지막좌표가_같으면_ring_closed로_인정한다() {
    List<List<BigDecimal>> ring =
        List.of(
            point("126.950000", "37.550000"),
            point("126.960000", "37.550000"),
            point("126.960000", "37.560000"),
            point("126.9500000", "37.5500000"));

    List<List<BigDecimal>> result = validator.validateAndCanonicalizePolygonRing(ring);

    assertThat(result).hasSize(4);
    assertThat(result.get(0).get(0)).isEqualByComparingTo(result.get(3).get(0));
    assertThat(result.get(0).get(1)).isEqualByComparingTo(result.get(3).get(1));
  }

  private List<BigDecimal> point(String lon, String lat) {
    return List.of(new BigDecimal(lon), new BigDecimal(lat));
  }
}
