package com.surimap.marker.domain.port;

import org.locationtech.jts.geom.Geometry;

import java.util.Optional;
import java.util.UUID;

/**
 * S2 map_boundary 조회 port.
 * L3(김희수)가 구현하고, L5가 소비한다.
 *
 * @see docs/contracts/L5-05-geometry-spec.md §4
 */
public interface MapBoundaryQueryPort {

    /**
     * 사건의 현재 ACTIVE map_boundary geometry를 반환한다.
     *
     * @param incidentId 사건 ID
     * @return ACTIVE boundary의 Polygon geometry, 없으면 empty
     */
    Optional<Geometry> findActiveBoundary(UUID incidentId);
}
