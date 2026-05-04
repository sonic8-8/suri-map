package com.surimap.maparea.geometry.validation;

import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.util.List;

/**
 * 도형 검증 결과를 담는 값 객체다.
 *
 * @param canonicalOuterRing 정규화와 1차 검증이 끝난 outer ring
 * @param polygon JTS Polygon 객체
 * @param wkt PostGIS 저장·검증에 사용할 WKT 표현
 */
public record GeometryValidationResult(

        /** 소수점 6자리 정규화, 연속 중복 제거, ring closure 검증이 끝난 outer ring */
        List<List<BigDecimal>> canonicalOuterRing,

        /** JTS Polygon 객체 */
        Polygon polygon,

        /** PostGIS 검증과 저장에 사용할 WKT 문자열 */
        String wkt
) {

    /**
     * 검증 결과를 생성한다.
     *
     * @param canonicalOuterRing 검증과 정규화를 통과한 outer ring 좌표 목록
     * @param polygon 정규화된 outer ring으로 생성한 JTS Polygon
     * @return 정규화된 좌표 목록, JTS Polygon, WKT 문자열을 담은 검증 결과
     */
    public static GeometryValidationResult of(
            List<List<BigDecimal>> canonicalOuterRing,
            Polygon polygon
    ) {
        return new GeometryValidationResult(
                canonicalOuterRing,
                polygon,
                polygon.toText() // Polygon을 WKT 문자열로 변환
        );
    }
}
