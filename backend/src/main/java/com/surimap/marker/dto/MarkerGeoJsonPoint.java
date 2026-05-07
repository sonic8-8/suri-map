package com.surimap.marker.dto;

import com.surimap.marker.domain.exception.InvalidGeometryException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public record MarkerGeoJsonPoint(String type, List<BigDecimal> coordinates) {

  private static final int SRID = 4326;
  private static final int SCALE = 6;
  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), SRID);

  public Point toPoint() {
    if (!"Point".equals(type) || coordinates == null || coordinates.size() != 2) {
      throw new InvalidGeometryException("marker.location must be GeoJSON Point");
    }
    BigDecimal lon = coordinates.get(0);
    BigDecimal lat = coordinates.get(1);
    if (lon == null || lat == null) {
      throw new InvalidGeometryException("marker.location coordinates are required");
    }
    Point point =
        GEOMETRY_FACTORY.createPoint(new Coordinate(lon.doubleValue(), lat.doubleValue()));
    point.setSRID(SRID);
    return point;
  }

  public MarkerGeoJsonPoint canonical() {
    Point point = toPoint();
    return from(point);
  }

  public static MarkerGeoJsonPoint from(Point point) {
    if (point == null || point.isEmpty()) {
      throw new InvalidGeometryException("marker.location point is empty");
    }
    return new MarkerGeoJsonPoint(
        "Point", List.of(canonical(point.getX()), canonical(point.getY())));
  }

  private static BigDecimal canonical(double value) {
    return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
  }
}
