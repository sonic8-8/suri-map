package com.surimap.maparea.fixturetest;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.fixture.GeometryFixtures;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T04A canonical geometry fixture exactness 테스트.
 *
 * <p>이 테스트는 S2 하네스 문서의 고정 좌표, bbox, shared marker coordinate가 fixture에서 바뀌면 실패하는 mock contract
 * evidence다.
 */
@DisplayName("L3-T04A canonical geometry fixture exactness")
class GeometryFixtureExactnessTest {

  @Test
  @DisplayName("하네스 geometry 정책값이 S2 문서 fixture와 일치한다")
  void geometry_policy_fixture_값을_고정한다() {
    assertThat(GeometryFixtures.CRS).isEqualTo("EPSG:4326");
    assertThat(GeometryFixtures.COORDINATE_ORDER).isEqualTo("[lon, lat]");

    assertThat(GeometryFixtures.FIXTURE_BBOX_MIN_LON).isEqualTo(new BigDecimal("126.647507"));
    assertThat(GeometryFixtures.FIXTURE_BBOX_MIN_LAT).isEqualTo(new BigDecimal("35.052595"));
    assertThat(GeometryFixtures.FIXTURE_BBOX_MAX_LON).isEqualTo(new BigDecimal("127.017482"));
    assertThat(GeometryFixtures.FIXTURE_BBOX_MAX_LAT).isEqualTo(new BigDecimal("35.256837"));

    assertThat(GeometryFixtures.MINIMUM_POLYGON_AREA_M2).isEqualTo(400);
    assertThat(GeometryFixtures.MINIMUM_POLYGON_AREA_M2_VALUE).isEqualTo(new BigDecimal("400"));
    assertThat(GeometryFixtures.COORDINATE_PRECISION_DECIMALS).isEqualTo(6);
  }

  @Test
  @DisplayName("canonical overall_search_area는 광주 osm-local style bounds 안에 있다")
  void canonical_overall_search_area_fits_gwangju_osm_local_bounds() {
    assertThat(GeometryFixtures.BOUNDARY_GEOMETRY_BBOX.get(0))
        .isGreaterThanOrEqualTo(new BigDecimal("126.647507"));
    assertThat(GeometryFixtures.BOUNDARY_GEOMETRY_BBOX.get(1))
        .isGreaterThanOrEqualTo(new BigDecimal("35.052595"));
    assertThat(GeometryFixtures.BOUNDARY_GEOMETRY_BBOX.get(2))
        .isLessThanOrEqualTo(new BigDecimal("127.017482"));
    assertThat(GeometryFixtures.BOUNDARY_GEOMETRY_BBOX.get(3))
        .isLessThanOrEqualTo(new BigDecimal("35.256837"));
  }

  @Test
  @DisplayName("overall_search_area fixture는 문서 좌표와 bbox 순서를 그대로 유지한다")
  void overall_search_area_fixture_exactness를_검증한다() {
    List<List<BigDecimal>> expectedRing =
        List.of(
            point("126.904000", "35.158000"),
            point("126.923000", "35.158000"),
            point("126.923000", "35.173000"),
            point("126.904000", "35.173000"),
            point("126.904000", "35.158000"));
    List<BigDecimal> expectedBbox = bbox("126.904000", "35.158000", "126.923000", "35.173000");

    GeoJsonPolygon polygon = GeometryFixtures.validOverallSearchAreaPolygon();

    assertThat(GeometryFixtures.validOverallSearchAreaRing()).isEqualTo(expectedRing);
    assertThat(polygon.type()).isEqualTo("Polygon");
    assertThat(polygon.coordinates()).containsExactly(expectedRing);
    assertThat(GeometryFixtures.BOUNDARY_GEOMETRY_BBOX).isEqualTo(expectedBbox);
    assertAllCoordinatesHaveScale(GeometryFixtures.validOverallSearchAreaRing(), 6);
  }

  @Test
  @DisplayName("search_area fixture는 문서 좌표와 bbox 순서를 그대로 유지한다")
  void search_area_fixture_exactness를_검증한다() {
    List<List<BigDecimal>> expectedRing =
        List.of(
            point("126.910000", "35.160000"),
            point("126.918000", "35.160000"),
            point("126.918000", "35.166000"),
            point("126.910000", "35.166000"),
            point("126.910000", "35.160000"));
    List<BigDecimal> expectedBbox = bbox("126.910000", "35.160000", "126.918000", "35.166000");

    GeoJsonPolygon polygon = GeometryFixtures.validSearchAreaPolygon();

    assertThat(GeometryFixtures.validSearchAreaRing()).isEqualTo(expectedRing);
    assertThat(polygon.type()).isEqualTo("Polygon");
    assertThat(polygon.coordinates()).containsExactly(expectedRing);
    assertThat(GeometryFixtures.AREA_GEOMETRY_BBOX).isEqualTo(expectedBbox);
    assertAllCoordinatesHaveScale(GeometryFixtures.validSearchAreaRing(), 6);
  }

  @Test
  @DisplayName("shared marker와 invalid coordinate fixture 값을 고정한다")
  void shared_marker와_invalid_coordinate_fixture를_고정한다() {
    assertThat(GeometryFixtures.referenceMarkerPoint()).isEqualTo(point("126.913400", "35.163100"));
    assertThat(GeometryFixtures.invalidCoordOutsideEnvelope())
        .isEqualTo(point("127.200000", "35.163100"));
    assertThat(GeometryFixtures.invalidCoordLatLonSwapped())
        .isEqualTo(point("35.163100", "126.913400"));
    assertThat(GeometryFixtures.overPrecisionPoint()).isEqualTo(point("126.9134007", "35.1631007"));
    assertThat(GeometryFixtures.overPrecisionPoint())
        .allSatisfy(coordinate -> assertThat(coordinate.scale()).isEqualTo(7));

    assertThat(GeometryFixtures.INVALID_CASES_WITHOUT_FIXED_COORDINATES)
        .containsExactly(
            "polygon-unclosed",
            "polygon-self-intersecting",
            "polygon-too-small-under-400m2",
            "point-null-nan");
  }

  private static List<BigDecimal> bbox(String minLon, String minLat, String maxLon, String maxLat) {
    return List.of(
        new BigDecimal(minLon),
        new BigDecimal(minLat),
        new BigDecimal(maxLon),
        new BigDecimal(maxLat));
  }

  private static List<BigDecimal> point(String lon, String lat) {
    return List.of(new BigDecimal(lon), new BigDecimal(lat));
  }

  private static void assertAllCoordinatesHaveScale(List<List<BigDecimal>> ring, int scale) {
    for (List<BigDecimal> point : ring) {
      assertThat(point).allSatisfy(coordinate -> assertThat(coordinate.scale()).isEqualTo(scale));
    }
  }
}
