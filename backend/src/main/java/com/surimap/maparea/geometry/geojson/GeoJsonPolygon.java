package com.surimap.maparea.geometry.geojson;

import java.math.BigDecimal;
import java.util.List;

/**
 * GeoJSON Polygon 요청값.
 *
 * <p>S2 지도 경계와 수색 구역 API에서 입력으로 받는 Polygon 구조를 표현한다.</p>
 * <p>이 record는 입력 형태만 담고, 좌표 유효성 검증은 수행하지 않는다.</p>
 *
 * <p>GeoJSON Polygon의 기본 형태는 다음과 같다.</p>
 * <pre>
 * {
 *   "type": "Polygon",
 *   "coordinates": [
 *     [
 *       [lon, lat],
 *       [lon, lat],
 *       [lon, lat],
 *       [lon, lat]
 *     ]
 *   ]
 * }
 * </pre>
 *
 * @param type GeoJSON 도형 타입. 이 요청값에서는 {@code Polygon}만 허용한다.
 * @param coordinates GeoJSON Polygon 좌표 배열
 */
public record GeoJsonPolygon(

        /** GeoJSON geometry type. 이 요청값에서는 Polygon만 허용한다. */
        String type,

        /** GeoJSON Polygon coordinates 배열 */
        List<List<List<BigDecimal>>> coordinates
) {

    /**
     * Polygon의 outer ring을 반환한다.
     *
     * @return Polygon outer ring 좌표 목록
     */
    public List<List<BigDecimal>> outerRing() {
        if (coordinates == null || coordinates.isEmpty()) {
            return null;
        }

        return coordinates.get(0);
    }
}
