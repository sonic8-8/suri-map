package com.surimap.marker.domain.port;

import java.util.UUID;
import org.locationtech.jts.geom.Point;

/**
 * 마커 위치 검증 port. 구역 밖 단서·발견도 현장 기록으로 저장될 수 있으므로 구역 포함 여부는
 * invalid_geometry 조건으로 보지 않는다.
 *
 * @see docs/spec/specs/S5.json
 * @see docs/spec/boundaries.md
 */
public interface MarkerLocationValidator {

  /**
   * 마커 좌표를 검증한다.
   *
   * <ul>
   *   <li>Point type 필수
   *   <li>coordinates [lon, lat] 정확히 2개
   *   <li>lon ∈ [-180, 180], lat ∈ [-90, 90]
   *   <li>null/empty/NaN/SRID 불일치 거부
   * </ul>
   *
   * @param incidentId 사건 ID
   * @param location 마커 좌표 (EPSG:4326)
   * @throws InvalidGeometryException 검증 실패 시
   */
  void validate(UUID incidentId, Point location);
}
