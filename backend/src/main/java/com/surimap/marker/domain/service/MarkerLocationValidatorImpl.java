package com.surimap.marker.domain.service;

import com.surimap.marker.domain.exception.InvalidGeometryException;
import com.surimap.marker.domain.port.MapBoundaryQueryPort;
import com.surimap.marker.domain.port.MarkerLocationValidator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.util.Optional;
import java.util.UUID;

/**
 * 마커 위치 검증 구현체.
 *
 * 검증 순서:
 * 1. null 체크
 * 2. 좌표 유효성 (NaN, 범위)
 * 3. precision 6자리 정규화
 * 4. map_boundary 내 포함 확인
 *
 * @see docs/contracts/L5-05-geometry-spec.md
 */
public class MarkerLocationValidatorImpl implements MarkerLocationValidator {

    private static final int PRECISION_DIGITS = 6;
    private static final double PRECISION_FACTOR = Math.pow(10, PRECISION_DIGITS);

    private final MapBoundaryQueryPort boundaryQuery;

    public MarkerLocationValidatorImpl(MapBoundaryQueryPort boundaryQuery) {
        this.boundaryQuery = boundaryQuery;
    }

    @Override
    public void validate(UUID incidentId, Point location) {
        // 1. null 체크
        if (location == null) {
            throw new InvalidGeometryException("location is null");
        }

        Coordinate coord = location.getCoordinate();

        // 2. NaN 체크
        if (Double.isNaN(coord.x) || Double.isNaN(coord.y)) {
            throw new InvalidGeometryException("coordinates contain NaN");
        }

        double lon = coord.x;
        double lat = coord.y;

        // 3. 경도/위도 범위 검증
        if (lon < -180.0 || lon > 180.0) {
            throw new InvalidGeometryException("longitude out of range: " + lon);
        }
        if (lat < -90.0 || lat > 90.0) {
            throw new InvalidGeometryException("latitude out of range: " + lat);
        }

        // 4. lon/lat 뒤바뀜 감지 (lat 자리에 경도 범위 값이 있는 경우)
        if (lat > 90.0 || lat < -90.0) {
            throw new InvalidGeometryException("possible lat/lon swap detected");
        }

        // 5. precision 6자리 정규화
        double normalizedLon = truncate(lon);
        double normalizedLat = truncate(lat);

        // 6. map_boundary 내 포함 확인
        Optional<Geometry> boundary = boundaryQuery.findActiveBoundary(incidentId);
        if (boundary.isEmpty()) {
            throw new InvalidGeometryException("no active map_boundary for incident");
        }

        Point normalizedPoint = location.getFactory().createPoint(
                new Coordinate(normalizedLon, normalizedLat));
        normalizedPoint.setSRID(4326);

        if (!boundary.get().covers(normalizedPoint)) {
            throw new InvalidGeometryException(
                    "point [" + normalizedLon + ", " + normalizedLat + "] outside map_boundary");
        }
    }

    /**
     * 소수점 6자리 truncate (반올림 아님).
     */
    private static double truncate(double value) {
        return Math.floor(value * PRECISION_FACTOR) / PRECISION_FACTOR;
    }
}
