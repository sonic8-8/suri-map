package com.surimap.maparea.geometry.geojson;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * JTS Polygon을 S2 query contract의 canonical GeoJSON/bbox shape로 변환한다.
 */
public final class GeoJsonPolygonConverter {

    private static final int CANONICAL_SCALE = 6;

    private GeoJsonPolygonConverter() {}

    /**
     * JTS Polygon을 GeoJSON Polygon으로 변환한다.
     *
     * @param polygon JTS Polygon
     * @return canonical GeoJSON Polygon
     */
    public static GeoJsonPolygon toGeoJsonPolygon(Polygon polygon) {
        Coordinate[] coordinates = polygon.getExteriorRing().getCoordinates();
        List<List<BigDecimal>> outerRing = new ArrayList<>(coordinates.length);

        for (Coordinate coordinate : coordinates) {
            outerRing.add(List.of(
                    canonical(coordinate.x),
                    canonical(coordinate.y)
            ));
        }

        return new GeoJsonPolygon("Polygon", List.of(outerRing));
    }

    /**
     * JTS Polygon의 bbox를 [minLon, minLat, maxLon, maxLat] 순서로 반환한다.
     *
     * @param polygon JTS Polygon
     * @return EPSG:4326 bbox
     */
    public static List<BigDecimal> bboxOf(Polygon polygon) {
        Envelope envelope = polygon.getEnvelopeInternal();
        return List.of(
                canonical(envelope.getMinX()),
                canonical(envelope.getMinY()),
                canonical(envelope.getMaxX()),
                canonical(envelope.getMaxY())
        );
    }

    private static BigDecimal canonical(double value) {
        return BigDecimal.valueOf(value).setScale(CANONICAL_SCALE, RoundingMode.HALF_UP);
    }
}
