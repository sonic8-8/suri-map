package com.surimap.marker.domain.port;

import java.util.UUID;
import org.locationtech.jts.geom.Point;

/**
 * 마커 위치 검증 port. 구현체는 S2 SearchAreaQuery.overallOf 계약을 소비하여 overall_search_area 포함 검증을 수행한다.
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
   *   <li>precision 6자리 canonical 정규화
   *   <li>overall_search_area 내 포함
   * </ul>
   *
   * @param incidentId 사건 ID (SearchAreaQuery.overallOf 조회용)
   * @param location 마커 좌표 (EPSG:4326)
   * @throws InvalidGeometryException 검증 실패 시
   */
  void validate(UUID incidentId, Point location);
}
