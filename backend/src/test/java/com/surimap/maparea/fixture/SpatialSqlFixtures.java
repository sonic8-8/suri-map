package com.surimap.maparea.fixture;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

/**
 * PostGIS/MyBatis 통합 테스트에서 사용하는 WKT/JTS fixture.
 *
 * <p>좌표는 {@link GeometryFixtures}의 canonical polygon과 같은 EPSG:4326, lon/lat 순서를 따른다.
 * 이 클래스는 SQL mapper와 TypeHandler 검증용 문자열/JTS 객체만 제공한다.</p>
 */
public final class SpatialSqlFixtures {

    /** map_boundary 저장, active boundary 조회, parent containment 검증에 쓰는 기준 Polygon. */
    public static final String BOUNDARY_WKT =
            "POLYGON ((126.948000 37.565000, 126.968000 37.565000, "
                    + "126.968000 37.579000, 126.948000 37.579000, "
                    + "126.948000 37.565000))";

    /** search_area 저장, bbox 조회, TypeHandler round-trip 검증에 쓰는 기준 Polygon. */
    public static final String SEARCH_AREA_WKT =
            "POLYGON ((126.952000 37.568000, 126.961000 37.568000, "
                    + "126.961000 37.575000, 126.952000 37.575000, "
                    + "126.952000 37.568000))";

    /** 최소 면적 400m2 미만 거부 검증용 Polygon. */
    public static final String TINY_AREA_WKT =
            "POLYGON ((126.952000 37.568000, 126.952010 37.568000, "
                    + "126.952010 37.568010, 126.952000 37.568010, "
                    + "126.952000 37.568000))";

    /** BOUNDARY_WKT 밖에 있어 parent containment 실패를 검증하는 Polygon. */
    public static final String OUTSIDE_BOUNDARY_WKT =
            "POLYGON ((126.970000 37.568000, 126.972000 37.568000, "
                    + "126.972000 37.570000, 126.970000 37.570000, "
                    + "126.970000 37.568000))";

    /**
     * SEARCH_AREA_WKT와 외곽선만 맞닿는 Polygon.
     * ST_Covers containment는 허용하고, 면적 기반 overlap 검증에서는 겹침으로 보지 않는다.
     */
    public static final String TOUCHING_AREA_WKT =
            "POLYGON ((126.961000 37.568000, 126.965000 37.568000, "
                    + "126.965000 37.575000, 126.961000 37.575000, "
                    + "126.961000 37.568000))";

    private SpatialSqlFixtures() {}

    /** JtsGeometryTypeHandlerIntegrationTest에서 저장/조회 round-trip에 쓰는 search_area Polygon. */
    public static Polygon searchAreaPolygon() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Coordinate[] coordinates = {
                new Coordinate(126.952000, 37.568000),
                new Coordinate(126.961000, 37.568000),
                new Coordinate(126.961000, 37.575000),
                new Coordinate(126.952000, 37.575000),
                new Coordinate(126.952000, 37.568000)
        };
        LinearRing shell = geometryFactory.createLinearRing(coordinates);
        Polygon polygon = geometryFactory.createPolygon(shell);
        polygon.setSRID(4326);
        return polygon;
    }
}
