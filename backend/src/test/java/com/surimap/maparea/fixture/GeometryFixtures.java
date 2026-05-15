package com.surimap.maparea.fixture;

import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import java.math.BigDecimal;
import java.util.List;

/**
 * S2 geometry validation에서 쓰는 SC-04 좌표 fixture 모음.
 *
 * <p>기준 문서: docs/spec/specs/S2.json geometry rules, docs/spec/harness-scenarios.md geometry/GPS
 * fixture.
 */
public final class GeometryFixtures {

  // TODO: S2 geometry value object/constant가 생기면 CRS, GeoJSON type, invalid case 이름 문자열을 해당 타입 기준으로
  // 교체한다.

  /** S2 geometry는 EPSG:4326 좌표계만 허용한다. */
  public static final String CRS = "EPSG:4326";

  /** 모든 좌표는 [lon, lat] 순서로 해석한다. */
  public static final String COORDINATE_ORDER = "[lon, lat]";

  /** SC-04 하네스 좌표가 들어와야 하는 fixture bbox(bounding box). 즉, 테스트 시나리오에서 좌표가 들어와야 하는 사각형 범위 */
  public static final BigDecimal FIXTURE_BBOX_MIN_LON = new BigDecimal("126.647507");

  public static final BigDecimal FIXTURE_BBOX_MIN_LAT = new BigDecimal("35.052595");
  public static final BigDecimal FIXTURE_BBOX_MAX_LON = new BigDecimal("127.017482");
  public static final BigDecimal FIXTURE_BBOX_MAX_LAT = new BigDecimal("35.256837");

  /** polygon으로 인정하는 최소 면적과 canonical 좌표 정밀도. */
  public static final int MINIMUM_POLYGON_AREA_M2 = 400;

  public static final BigDecimal MINIMUM_POLYGON_AREA_M2_VALUE = new BigDecimal("400");
  public static final int COORDINATE_PRECISION_DECIMALS = 6;

  /** validOverallSearchAreaRing()의 EPSG:4326 bbox: [minLon, minLat, maxLon, maxLat]. */
  public static final List<BigDecimal> BOUNDARY_GEOMETRY_BBOX =
      List.of(
          new BigDecimal("126.904000"),
          new BigDecimal("35.158000"),
          new BigDecimal("126.923000"),
          new BigDecimal("35.173000"));

  /** validSearchAreaRing()의 EPSG:4326 bbox: [minLon, minLat, maxLon, maxLat]. */
  public static final List<BigDecimal> AREA_GEOMETRY_BBOX =
      List.of(
          new BigDecimal("126.910000"),
          new BigDecimal("35.160000"),
          new BigDecimal("126.918000"),
          new BigDecimal("35.166000"));

  private GeometryFixtures() {}

  /** 정상 overall_search_area Polygon outer ring (harness-scenarios.md §6). */
  public static List<List<BigDecimal>> validOverallSearchAreaRing() {
    return List.of(
        point("126.904000", "35.158000"),
        point("126.923000", "35.158000"),
        point("126.923000", "35.173000"),
        point("126.904000", "35.173000"),
        point("126.904000", "35.158000"));
  }

  /** 정상 search_area Polygon outer ring (harness-scenarios.md §6). */
  public static List<List<BigDecimal>> validSearchAreaRing() {
    return List.of(
        point("126.910000", "35.160000"),
        point("126.918000", "35.160000"),
        point("126.918000", "35.166000"),
        point("126.910000", "35.166000"),
        point("126.910000", "35.160000"));
  }

  /**
   * 정상 marker 기준 좌표.
   *
   * <p>S5 owned reference. L3는 boundary/area validation reference 용도로만 참조.
   */
  public static List<BigDecimal> referenceMarkerPoint() {
    return point("126.913400", "35.163100");
  }

  /** coord-outside-envelope: bbox 밖 좌표. 기대 동작: invalid_geometry 거부. */
  public static List<BigDecimal> invalidCoordOutsideEnvelope() {
    return point("127.200000", "35.163100");
  }

  /** coord-latlon-swapped: lat/lon 순서가 뒤바뀐 좌표. 기대 동작: invalid_geometry 거부. */
  public static List<BigDecimal> invalidCoordLatLonSwapped() {
    return point("35.163100", "126.913400");
  }

  /** precision-over-6dp: 소수점 7자리 좌표. 기대 동작: 6자리로 반올림해 canonical coordinate로 정규화. */
  public static List<BigDecimal> overPrecisionPoint() {
    return point("126.9134007", "35.1631007");
  }

  /** 좌표값이 docs에 고정되지 않은 invalid 케이스 이름 목록. 각 테스트가 적절한 좌표 조합을 자체 생성한다. */
  public static final List<String> INVALID_CASES_WITHOUT_FIXED_COORDINATES =
      List.of(
          "polygon-unclosed",
          "polygon-self-intersecting",
          "polygon-too-small-under-400m2",
          "point-null-nan");

  /** 정상 overall_search_area GeoJSON Polygon 응답 shape. */
  public static GeoJsonPolygon validOverallSearchAreaPolygon() {
    return new GeoJsonPolygon("Polygon", List.of(validOverallSearchAreaRing()));
  }

  /** 정상 search_area GeoJSON Polygon 응답 shape. */
  public static GeoJsonPolygon validSearchAreaPolygon() {
    return new GeoJsonPolygon("Polygon", List.of(validSearchAreaRing()));
  }

  private static List<BigDecimal> point(String lon, String lat) {
    return List.of(new BigDecimal(lon), new BigDecimal(lat));
  }
}
