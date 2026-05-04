package com.surimap.maparea.geometry.validation;

import java.math.BigDecimal;

/**
 * PostGIS가 필요한 공간 검증을 수행하는 포트.
 *
 * <p>좌표 구조나 ring 닫힘처럼 Java 코드만으로 확인 가능한 검증은
 * {@link GeometryValidator}가 담당한다.</p>
 * <p>이 포트는 실제 면적, Polygon 포함 관계, Polygon 간 면적 겹침처럼
 * PostGIS 공간 연산이 필요한 검증을 담당한다.</p>
 */
public interface GeometrySpatialPort {

    /**
     * Polygon의 실제 면적이 기준 m2 이상인지 확인한다.
     *
     * @param polygonWkt Polygon WKT
     * @param minimumAreaM2 최소 면적 기준 m2
     * @return 기준 이상이면 true
     */
    boolean isAreaAtLeastM2(String polygonWkt, BigDecimal minimumAreaM2);

    /**
     * child Polygon이 parent Polygon 내부에 완전히 포함되는지 확인한다.
     *
     * @param parentPolygonWkt parent Polygon WKT
     * @param childPolygonWkt child Polygon WKT
     * @return parent가 child를 cover하면 true
     */
    boolean covers(String parentPolygonWkt, String childPolygonWkt);

    /**
     * 두 Polygon이 면적 기준으로 겹치는지 확인한다.
     *
     * <p>경계선만 닿는 것은 overlap으로 보지 않는다.</p>
     *
     * @param firstPolygonWkt 첫 번째 Polygon WKT
     * @param secondPolygonWkt 두 번째 Polygon WKT
     * @return 면적이 겹치면 true
     */
    boolean overlapsByArea(String firstPolygonWkt, String secondPolygonWkt);
}
