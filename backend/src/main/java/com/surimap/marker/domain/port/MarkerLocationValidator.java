package com.surimap.marker.domain.port;

import org.locationtech.jts.geom.Point;

import java.util.UUID;

/**
 * 마커 위치 검증 port.
 * 구현체는 MapBoundaryQueryPort를 소비하여 ST_Contains 검증을 수행한다.
 *
 * @see docs/contracts/L5-05-geometry-spec.md
 */
public interface MarkerLocationValidator {

    /**
     * 마커 좌표를 검증한다.
     *
     * <ul>
     *   <li>Point type 필수</li>
     *   <li>coordinates [lon, lat] 정확히 2개</li>
     *   <li>lon ∈ [-180, 180], lat ∈ [-90, 90]</li>
     *   <li>null/NaN 거부</li>
     *   <li>precision 6자리 정규화</li>
     *   <li>map_boundary 내 포함 (ST_Contains)</li>
     * </ul>
     *
     * @param incidentId 사건 ID (map_boundary 조회용)
     * @param location   마커 좌표 (EPSG:4326)
     * @throws InvalidGeometryException 검증 실패 시
     */
    void validate(UUID incidentId, Point location);
}
