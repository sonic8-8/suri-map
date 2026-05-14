package com.surimap.offlinepackage.service;

import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.offlinepackage.exception.TileUnavailableException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

final class OverallSearchAreaTileCoverage {

  private static final double WEB_MERCATOR_MAX_LAT = 85.05112878;
  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(), 4326);

  private OverallSearchAreaTileCoverage() {}

  static List<TileCoordinate> covering(
      GeoJsonPolygon overallSearchArea, String styleId, int minZoom, int maxZoom) {
    if (styleId == null || styleId.isBlank() || minZoom < 0 || maxZoom < minZoom || maxZoom > 30) {
      throw new TileUnavailableException();
    }

    Polygon area = polygon(overallSearchArea);
    Envelope envelope = area.getEnvelopeInternal();
    if (envelope.isNull()) {
      throw new TileUnavailableException();
    }

    List<TileCoordinate> tiles = new ArrayList<>();
    for (int zoom = minZoom; zoom <= maxZoom; zoom++) {
      int minX = lonToTileX(envelope.getMinX(), zoom);
      int maxX = lonToTileX(envelope.getMaxX(), zoom);
      int minY = latToTileY(envelope.getMaxY(), zoom);
      int maxY = latToTileY(envelope.getMinY(), zoom);

      for (int x = minX; x <= maxX; x++) {
        for (int y = minY; y <= maxY; y++) {
          if (area.intersects(tileBounds(zoom, x, y))) {
            tiles.add(new TileCoordinate(styleId, zoom, x, y));
          }
        }
      }
    }
    return List.copyOf(tiles);
  }

  private static Polygon polygon(GeoJsonPolygon polygon) {
    if (polygon == null || polygon.coordinates() == null || polygon.coordinates().isEmpty()) {
      throw new TileUnavailableException();
    }

    LinearRing shell = ring(polygon.coordinates().get(0));
    LinearRing[] holes =
        polygon.coordinates().stream()
            .skip(1)
            .map(OverallSearchAreaTileCoverage::ring)
            .toArray(LinearRing[]::new);
    Polygon geometry = GEOMETRY_FACTORY.createPolygon(shell, holes);
    geometry.setSRID(4326);
    if (geometry.isEmpty() || !geometry.isValid()) {
      throw new TileUnavailableException();
    }
    return geometry;
  }

  private static LinearRing ring(List<List<BigDecimal>> positions) {
    if (positions == null || positions.size() < 4) {
      throw new TileUnavailableException();
    }

    List<Coordinate> coordinates = new ArrayList<>(positions.size() + 1);
    for (List<BigDecimal> position : positions) {
      if (position == null
          || position.size() < 2
          || position.get(0) == null
          || position.get(1) == null) {
        throw new TileUnavailableException();
      }
      coordinates.add(new Coordinate(position.get(0).doubleValue(), position.get(1).doubleValue()));
    }
    Coordinate first = coordinates.get(0);
    Coordinate last = coordinates.get(coordinates.size() - 1);
    if (!first.equals2D(last)) {
      coordinates.add(new Coordinate(first));
    }
    return GEOMETRY_FACTORY.createLinearRing(coordinates.toArray(Coordinate[]::new));
  }

  private static Polygon tileBounds(int zoom, int x, int y) {
    double west = tileXToLon(x, zoom);
    double east = tileXToLon(x + 1, zoom);
    double north = tileYToLat(y, zoom);
    double south = tileYToLat(y + 1, zoom);
    return GEOMETRY_FACTORY.createPolygon(
        new Coordinate[] {
          new Coordinate(west, south),
          new Coordinate(east, south),
          new Coordinate(east, north),
          new Coordinate(west, north),
          new Coordinate(west, south)
        });
  }

  private static int lonToTileX(double lon, int zoom) {
    int tileCount = 1 << zoom;
    double clampedLon = Math.max(-180.0, Math.min(180.0, lon));
    int x = (int) Math.floor(((clampedLon + 180.0) / 360.0) * tileCount);
    return clamp(x, 0, tileCount - 1);
  }

  private static int latToTileY(double lat, int zoom) {
    int tileCount = 1 << zoom;
    double clampedLat = Math.max(-WEB_MERCATOR_MAX_LAT, Math.min(WEB_MERCATOR_MAX_LAT, lat));
    double latRad = Math.toRadians(clampedLat);
    int y =
        (int)
            Math.floor(
                ((1.0 - Math.log(Math.tan(latRad) + (1.0 / Math.cos(latRad))) / Math.PI) / 2.0)
                    * tileCount);
    return clamp(y, 0, tileCount - 1);
  }

  private static double tileXToLon(int x, int zoom) {
    return x / (double) (1 << zoom) * 360.0 - 180.0;
  }

  private static double tileYToLat(int y, int zoom) {
    double n = Math.PI - (2.0 * Math.PI * y) / (1 << zoom);
    return Math.toDegrees(Math.atan(Math.sinh(n)));
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  record TileCoordinate(String styleId, int z, int x, int y) {}
}
