package com.surimap.maparea.geometry.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * S2 지도 경계와 수색 구역을 검증할 때 공통으로 사용하는 도형 정책.
 *
 * <p>이 클래스는 자기 교차, 면적 부족, bbox 이탈, ring 닫힘 오류 같은 Polygon의 유효성 문제를 직접 판정하지 않는다.
 *
 * <p>대신 실제 검증 로직이 어떤 기준으로 Polygon을 검사해야 하는지에 필요한 SRID, CRS, 좌표 순서, bbox 범위, 좌표 정밀도, 최소 면적 기준을 제공한다.
 *
 * @param srid S2에서 허용하는 공간 좌표계 SRID 값
 * @param crs S2에서 허용하는 공간 좌표계 이름
 * @param coordinateOrder GeoJSON 좌표 배열의 해석 순서
 * @param minLon S2 하네스 bbox의 최소 경도값
 * @param minLat S2 하네스 bbox의 최소 위도값
 * @param maxLon S2 하네스 bbox의 최대 경도값
 * @param maxLat S2 하네스 bbox의 최대 위도값
 * @param coordinatePrecisionDecimals S2 canonical 좌표 소수점 자릿수
 * @param minimumPolygonAreaM2 S2에서 허용하는 Polygon의 최소 면적 기준값
 * @param roundCoordinatesToPrecision 좌표를 canonical precision으로 반올림 정규화할지 여부
 * @param removeConsecutiveDuplicatePoints 연속으로 중복된 좌표를 제거할지 여부
 * @param requireRingClosureAfterNormalization 정규화 이후 Polygon ring 닫힘을 요구할지 여부
 */
public record GeometryPolicy(
    int srid,
    String crs,
    String coordinateOrder,
    BigDecimal minLon,
    BigDecimal minLat,
    BigDecimal maxLon,
    BigDecimal maxLat,
    int coordinatePrecisionDecimals,
    BigDecimal minimumPolygonAreaM2,
    boolean roundCoordinatesToPrecision,
    boolean removeConsecutiveDuplicatePoints,
    boolean requireRingClosureAfterNormalization) {

  /** S2에서 고정한 SRID 값 */
  private static final int REQUIRED_SRID = 4326;

  /** S2에서 고정한 CRS 이름 */
  private static final String REQUIRED_CRS = "EPSG:4326";

  /** S2에서 고정한 GeoJSON 좌표 순서 */
  private static final String REQUIRED_COORDINATE_ORDER = "[lon, lat]";

  /** S2에서 고정한 canonical 좌표 소수점 자릿수 */
  private static final int REQUIRED_PRECISION_DECIMALS = 6;

  /** S2에서 고정한 최소 Polygon 면적 기준 */
  private static final BigDecimal REQUIRED_MINIMUM_POLYGON_AREA_M2 = new BigDecimal("400");

  /** EPSG:4326에서 허용되는 최소 경도값 */
  private static final BigDecimal MIN_VALID_LON = new BigDecimal("-180");

  /** EPSG:4326에서 허용되는 최대 경도값 */
  private static final BigDecimal MAX_VALID_LON = new BigDecimal("180");

  /** EPSG:4326에서 허용되는 최소 위도값 */
  private static final BigDecimal MIN_VALID_LAT = new BigDecimal("-90");

  /** EPSG:4326에서 허용되는 최대 위도값 */
  private static final BigDecimal MAX_VALID_LAT = new BigDecimal("90");

  /** GeometryPolicy 생성 시 S2 스펙 불변 조건을 검증한다. */
  public GeometryPolicy {
    Objects.requireNonNull(crs, "좌표계 이름은 null일 수 없습니다.");
    Objects.requireNonNull(coordinateOrder, "좌표 순서 설명은 null일 수 없습니다.");
    Objects.requireNonNull(minLon, "최소 경도는 null일 수 없습니다.");
    Objects.requireNonNull(minLat, "최소 위도는 null일 수 없습니다.");
    Objects.requireNonNull(maxLon, "최대 경도는 null일 수 없습니다.");
    Objects.requireNonNull(maxLat, "최대 위도는 null일 수 없습니다.");
    Objects.requireNonNull(minimumPolygonAreaM2, "최소 Polygon 면적은 null일 수 없습니다.");

    if (srid != REQUIRED_SRID) {
      throw new IllegalArgumentException("S2 도형 정책의 SRID는 EPSG:4326만 허용합니다.");
    }

    if (!REQUIRED_CRS.equals(crs)) {
      throw new IllegalArgumentException("S2 도형 정책의 CRS는 EPSG:4326만 허용합니다.");
    }

    if (!REQUIRED_COORDINATE_ORDER.equals(coordinateOrder)) {
      throw new IllegalArgumentException("S2 GeoJSON 좌표 순서는 [lon, lat]만 허용합니다.");
    }

    if (!isValidLongitude(minLon) || !isValidLongitude(maxLon)) {
      throw new IllegalArgumentException("S2 bbox 경도는 EPSG:4326 경도 범위 안에 있어야 합니다.");
    }

    if (!isValidLatitude(minLat) || !isValidLatitude(maxLat)) {
      throw new IllegalArgumentException("S2 bbox 위도는 EPSG:4326 위도 범위 안에 있어야 합니다.");
    }

    if (minLon.compareTo(maxLon) >= 0) {
      throw new IllegalArgumentException("최소 경도는 최대 경도보다 작아야 합니다.");
    }

    if (minLat.compareTo(maxLat) >= 0) {
      throw new IllegalArgumentException("최소 위도는 최대 위도보다 작아야 합니다.");
    }

    if (coordinatePrecisionDecimals != REQUIRED_PRECISION_DECIMALS) {
      throw new IllegalArgumentException("S2 좌표 canonical precision은 소수점 6자리만 허용합니다.");
    }

    if (minimumPolygonAreaM2.compareTo(REQUIRED_MINIMUM_POLYGON_AREA_M2) != 0) {
      throw new IllegalArgumentException("S2 최소 Polygon 면적은 400㎡여야 합니다.");
    }

    if (!roundCoordinatesToPrecision) {
      throw new IllegalArgumentException("S2 좌표는 소수점 6자리 canonical 값으로 정규화해야 합니다.");
    }

    if (!removeConsecutiveDuplicatePoints) {
      throw new IllegalArgumentException("S2 Polygon 검증 전 연속 중복 좌표 제거 정책이 필요합니다.");
    }

    if (!requireRingClosureAfterNormalization) {
      throw new IllegalArgumentException("S2 Polygon은 정규화 이후 ring closure를 검증해야 합니다.");
    }
  }

  /**
   * S2 하네스 기준 기본 도형 정책을 생성한다.
   *
   * @return S2 하네스 기준 GeometryPolicy
   */
  public static GeometryPolicy s2HarnessDefault() {
    return new GeometryPolicy(
        REQUIRED_SRID,
        REQUIRED_CRS,
        REQUIRED_COORDINATE_ORDER,
        new BigDecimal("126.900000"),
        new BigDecimal("37.500000"),
        new BigDecimal("127.080000"),
        new BigDecimal("37.620000"),
        REQUIRED_PRECISION_DECIMALS,
        REQUIRED_MINIMUM_POLYGON_AREA_M2,
        true,
        true,
        true);
  }

  /**
   * 경도값이 EPSG:4326에서 표현 가능한 범위인지 확인한다.
   *
   * @param lon 검사할 경도값
   * @return 경도값이 -180 이상 180 이하이면 true
   */
  public static boolean isValidLongitude(BigDecimal lon) {
    if (lon == null) {
      return false;
    }

    return lon.compareTo(MIN_VALID_LON) >= 0 && lon.compareTo(MAX_VALID_LON) <= 0;
  }

  /**
   * 위도값이 EPSG:4326에서 표현 가능한 범위인지 확인한다.
   *
   * @param lat 검사할 위도값
   * @return 위도값이 -90 이상 90 이하이면 true
   */
  public static boolean isValidLatitude(BigDecimal lat) {
    if (lat == null) {
      return false;
    }

    return lat.compareTo(MIN_VALID_LAT) >= 0 && lat.compareTo(MAX_VALID_LAT) <= 0;
  }

  /**
   * 좌표가 S2 하네스 bbox 내부에 있는지 확인한다.
   *
   * <p>좌표 순서는 [경도, 위도] 기준이다.
   *
   * @param lon 검사할 경도값
   * @param lat 검사할 위도값
   * @return 좌표가 유효한 EPSG:4326 범위이면서 하네스 bbox 내부에 있으면 true
   */
  public boolean containsLonLat(BigDecimal lon, BigDecimal lat) {
    if (!isValidLongitude(lon) || !isValidLatitude(lat)) {
      return false;
    }

    return lon.compareTo(minLon) >= 0
        && lon.compareTo(maxLon) <= 0
        && lat.compareTo(minLat) >= 0
        && lat.compareTo(maxLat) <= 0;
  }

  /**
   * 좌표값을 S2 canonical precision으로 정규화한다.
   *
   * @param coordinate 정규화할 좌표값
   * @return 소수점 6자리로 반올림한 canonical 좌표값
   */
  public BigDecimal canonicalizeCoordinate(BigDecimal coordinate) {
    if (coordinate == null) {
      throw new IllegalArgumentException("좌표값은 null일 수 없습니다.");
    }

    return coordinate.setScale(coordinatePrecisionDecimals, RoundingMode.HALF_UP);
  }

  /**
   * 좌표가 이미 S2 canonical precision 이하인지 확인한다.
   *
   * <p>이 메서드는 invalid_geometry 여부를 판단하는 핵심 검증 메서드가 아니다.
   *
   * @param coordinate 검사할 좌표값
   * @return 좌표 소수점 자릿수가 S2 canonical precision 이하이면 true
   */
  public boolean isAlreadyCanonicalPrecision(BigDecimal coordinate) {
    if (coordinate == null) {
      return false;
    }

    return normalizedScale(coordinate) <= coordinatePrecisionDecimals;
  }

  /**
   * 좌표를 S2 canonical precision으로 정규화해야 하는지 확인한다.
   *
   * @return 좌표 정규화 정책이 활성화되어 있으면 true
   */
  public boolean shouldCanonicalizeCoordinates() {
    return roundCoordinatesToPrecision;
  }

  /**
   * 연속 중복 좌표를 제거해야 하는지 확인한다.
   *
   * @return 연속 중복 좌표 제거 정책이 활성화되어 있으면 true
   */
  public boolean shouldRemoveConsecutiveDuplicatePoints() {
    return removeConsecutiveDuplicatePoints;
  }

  /**
   * 정규화 이후 Polygon ring 닫힘을 검증해야 하는지 확인한다.
   *
   * @return 정규화 이후 ring closure 검증이 필요하면 true
   */
  public boolean shouldRequireRingClosureAfterNormalization() {
    return requireRingClosureAfterNormalization;
  }

  /**
   * BigDecimal 좌표값의 실질 소수점 자릿수를 계산한다.
   *
   * @param value 소수점 자릿수를 계산할 값
   * @return 끝의 0을 제거한 뒤의 소수점 자릿수
   */
  private int normalizedScale(BigDecimal value) {
    return Math.max(0, value.stripTrailingZeros().scale());
  }
}
