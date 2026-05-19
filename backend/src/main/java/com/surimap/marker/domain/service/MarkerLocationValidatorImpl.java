package com.surimap.marker.domain.service;

import com.surimap.marker.domain.exception.InvalidGeometryException;
import com.surimap.marker.domain.port.MarkerLocationValidator;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

/**
 * 마커 위치 검증 구현체.
 *
 * <p>검증 순서: 1. null/empty/SRID 체크 2. 좌표 유효성 (NaN, range). 전체 수색구역과 담당
 * 구역 밖 위치도 단서·발견·지원 요청이 발생할 수 있는 현장 기록이므로 저장을 막지 않는다.
 *
 * @see docs/spec/specs/S5.json
 * @see docs/spec/boundaries.md
 */
public class MarkerLocationValidatorImpl implements MarkerLocationValidator {

  private static final int SRID = 4326;

  public MarkerLocationValidatorImpl() {}

  @Override
  public void validate(UUID incidentId, Point location) {
    if (location == null || location.isEmpty()) {
      throw new InvalidGeometryException("location is null or empty");
    }
    if (location.getSRID() != SRID) {
      throw new InvalidGeometryException("location SRID must be 4326");
    }

    Coordinate coord = location.getCoordinate();
    if (coord == null) {
      throw new InvalidGeometryException("coordinates are empty");
    }

    if (!Double.isFinite(coord.x) || !Double.isFinite(coord.y)) {
      throw new InvalidGeometryException("coordinates must be finite numbers");
    }

    double lon = coord.x;
    double lat = coord.y;

    if (lon < -180.0 || lon > 180.0) {
      throw new InvalidGeometryException("longitude out of range: " + lon);
    }
    if (lat < -90.0 || lat > 90.0) {
      throw new InvalidGeometryException("latitude out of range: " + lat);
    }
  }
}
