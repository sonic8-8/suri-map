package com.surimap.marker.domain.service;

import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.marker.domain.exception.InvalidGeometryException;
import com.surimap.marker.domain.port.MarkerLocationValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * 마커 위치 검증 구현체.
 *
 * <p>검증 순서: 1. null/empty/SRID 체크 2. 좌표 유효성 (NaN, range) 3. precision 6자리 canonical 정규화 4.
 * SearchAreaQuery.overallOf 기반 overall_search_area 포함 확인
 *
 * @see docs/spec/specs/S5.json
 * @see docs/spec/boundaries.md
 */
public class MarkerLocationValidatorImpl implements MarkerLocationValidator {

  private static final int SRID = 4326;
  private static final int PRECISION_DIGITS = 6;
  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), SRID);

  private final SearchAreaQuery searchAreaQuery;

  public MarkerLocationValidatorImpl(SearchAreaQuery searchAreaQuery) {
    this.searchAreaQuery = Objects.requireNonNull(searchAreaQuery, "searchAreaQuery");
  }

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

    double normalizedLon = canonical(lon);
    double normalizedLat = canonical(lat);

    Point normalizedPoint =
        GEOMETRY_FACTORY.createPoint(new Coordinate(normalizedLon, normalizedLat));
    normalizedPoint.setSRID(4326);

    Polygon overallSearchArea =
        searchAreaQuery
            .overallOf(incidentId)
            .map(result -> toPolygon(result.geometry()))
            .orElseThrow(
                () -> new InvalidGeometryException("active overall_search_area is required"));

    if (!overallSearchArea.covers(normalizedPoint)) {
      throw new InvalidGeometryException(
          "point [" + normalizedLon + ", " + normalizedLat + "] outside overall_search_area");
    }
  }

  private static Polygon toPolygon(GeoJsonPolygon geoJsonPolygon) {
    if (geoJsonPolygon == null
        || !"Polygon".equals(geoJsonPolygon.type())
        || geoJsonPolygon.outerRing() == null
        || geoJsonPolygon.outerRing().isEmpty()) {
      throw new InvalidGeometryException("overall_search_area geometry is invalid");
    }

    List<List<BigDecimal>> outerRing = geoJsonPolygon.outerRing();
    Coordinate[] coordinates = new Coordinate[outerRing.size()];
    for (int index = 0; index < outerRing.size(); index++) {
      List<BigDecimal> point = outerRing.get(index);
      if (point == null || point.size() != 2) {
        throw new InvalidGeometryException("overall_search_area coordinate is invalid");
      }
      coordinates[index] = new Coordinate(point.get(0).doubleValue(), point.get(1).doubleValue());
    }

    Polygon polygon = GEOMETRY_FACTORY.createPolygon(coordinates);
    polygon.setSRID(SRID);
    return polygon;
  }

  private static double canonical(double value) {
    return BigDecimal.valueOf(value).setScale(PRECISION_DIGITS, RoundingMode.HALF_UP).doubleValue();
  }
}
