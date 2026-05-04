package com.surimap.maparea.geometry.validation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * PostGIS 공간 검증 SQL을 호출하는 MyBatis mapper interface.
 */
@Mapper
public interface GeometrySpatialMapper {

    /**
     * Polygon의 면적이 최소 기준 이상인지 확인한다.
     *
     * @param polygonWkt 검증할 Polygon WKT
     * @param minimumAreaM2 최소 면적 기준(m2)
     * @return 면적이 기준 이상이면 true
     */
    Boolean isAreaAtLeastM2(
            @Param("polygonWkt") String polygonWkt,
            @Param("minimumAreaM2") BigDecimal minimumAreaM2
    );

    /**
     * 자식 Polygon이 부모 Polygon 내부에 완전히 포함되는지 확인한다.
     * 부모 외곽선과 자식 외곽선이 맞닿거나 일부 공유되는 경우도 포함으로 본다.
     *
     * @param parentPolygonWkt 부모 Polygon WKT
     * @param childPolygonWkt 자식 Polygon WKT
     * @return 자식 Polygon이 부모 Polygon 내부에 완전히 포함되면 true
     */
    Boolean covers(
            @Param("parentPolygonWkt") String parentPolygonWkt,
            @Param("childPolygonWkt") String childPolygonWkt
    );

    /**
     * 두 Polygon이 면적을 가진 겹침 관계인지 확인한다.
     * 외곽선만 맞닿는 경우는 교집합 면적이 0이므로 겹침으로 보지 않는다.
     *
     * @param firstPolygonWkt 첫 번째 Polygon WKT
     * @param secondPolygonWkt 두 번째 Polygon WKT
     * @return 두 Polygon의 교집합 면적이 0보다 크면 true
     */
    Boolean overlapsByArea(
            @Param("firstPolygonWkt") String firstPolygonWkt,
            @Param("secondPolygonWkt") String secondPolygonWkt
    );
}
