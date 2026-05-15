package com.surimap.maparea.fixture;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

/**
 * PostGIS/MyBatis 통합 테스트에서 사용하는 WKT/JTS fixture.
 *
 * <p>좌표는 {@link GeometryFixtures}의 canonical polygon과 같은 EPSG:4326, lon/lat 순서를 따른다. 이 클래스는 SQL
 * mapper와 TypeHandler 검증용 문자열/JTS 객체만 제공한다.
 */
public final class SpatialSqlFixtures {

  /** overall_search_area 저장, active area 조회, parent containment 검증에 쓰는 기준 Polygon. */
  public static final String BOUNDARY_WKT =
      "POLYGON ((126.904000 35.158000, 126.923000 35.158000, "
          + "126.923000 35.173000, 126.904000 35.173000, "
          + "126.904000 35.158000))";

  /** search_area 저장, bbox 조회, TypeHandler round-trip 검증에 쓰는 기준 Polygon. */
  public static final String SEARCH_AREA_WKT =
      "POLYGON ((126.910000 35.160000, 126.918000 35.160000, "
          + "126.918000 35.166000, 126.910000 35.166000, "
          + "126.910000 35.160000))";

  /** 최소 면적 400m2 미만 거부 검증용 Polygon. */
  public static final String TINY_AREA_WKT =
      "POLYGON ((126.910000 35.160000, 126.910010 35.160000, "
          + "126.910010 35.160010, 126.910000 35.160010, "
          + "126.910000 35.160000))";

  /** BOUNDARY_WKT 밖에 있어 parent containment 실패를 검증하는 Polygon. */
  public static final String OUTSIDE_BOUNDARY_WKT =
      "POLYGON ((126.930000 35.160000, 126.932000 35.160000, "
          + "126.932000 35.162000, 126.930000 35.162000, "
          + "126.930000 35.160000))";

  /**
   * SEARCH_AREA_WKT와 외곽선만 맞닿는 Polygon. ST_Covers containment는 허용하고, 면적 기반 overlap 검증에서는 겹침으로 보지
   * 않는다.
   */
  public static final String TOUCHING_AREA_WKT =
      "POLYGON ((126.918000 35.160000, 126.925000 35.160000, "
          + "126.925000 35.166000, 126.918000 35.166000, "
          + "126.918000 35.160000))";

  private SpatialSqlFixtures() {}

  /** JtsGeometryTypeHandlerIntegrationTest에서 저장/조회 round-trip에 쓰는 search_area Polygon. */
  public static Polygon searchAreaPolygon() {
    GeometryFactory geometryFactory = new GeometryFactory();
    Coordinate[] coordinates = {
      new Coordinate(126.910000, 35.160000),
      new Coordinate(126.918000, 35.160000),
      new Coordinate(126.918000, 35.166000),
      new Coordinate(126.910000, 35.166000),
      new Coordinate(126.910000, 35.160000)
    };
    LinearRing shell = geometryFactory.createLinearRing(coordinates);
    Polygon polygon = geometryFactory.createPolygon(shell);
    polygon.setSRID(4326);
    return polygon;
  }
}
