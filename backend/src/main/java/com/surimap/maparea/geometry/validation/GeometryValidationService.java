package com.surimap.maparea.geometry.validation;

import com.surimap.maparea.geometry.exception.InvalidGeometryException;
import com.surimap.maparea.geometry.exception.MapBoundaryRequiredException;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.geometry.policy.GeometryPolicy;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * S2 map_boundary, search_area, split Polygon 검증 흐름을 담당하는 서비스.
 *
 * <p>이 서비스는 도메인 시나리오별로 필요한 검증 순서를 조립한다.</p>
 * <p>좌표 구조, ring 닫힘, bbox, 정규화 같은 기본 검증은 {@link GeometryValidator}에 위임한다.</p>
 * <p>실제 면적, boundary 내부 포함 여부, split child 간 겹침 여부처럼
 * PostGIS 공간 연산이 필요한 검증은 {@link GeometrySpatialPort}에 위임한다.</p>
 */
@Service
public class GeometryValidationService {

    // TODO L3-T01/T02A/T02B: API write path, PublishRequest, search_area_history transaction wiring은 별도 task에서 연결한다.

    /** 좌표와 Polygon ring 수준의 1차 검증기 */
    private final GeometryValidator geometryValidator;

    /** S2 도형 검증 기준값 */
    private final GeometryPolicy policy;

    /** PostGIS 기반 공간 검증 포트 */
    private final GeometrySpatialPort geometrySpatialPort;

    /** JTS Polygon 생성을 위한 GeometryFactory */
    private final GeometryFactory geometryFactory;

    /**
     * GeometryValidationService를 생성한다.
     *
     * @param geometryValidator 좌표와 ring 수준의 1차 검증기
     * @param policy S2 도형 정책
     * @param geometrySpatialPort PostGIS 기반 공간 검증 포트
     */
    public GeometryValidationService(
            GeometryValidator geometryValidator,
            GeometryPolicy policy,
            GeometrySpatialPort geometrySpatialPort
    ) {
        this.geometryValidator = Objects.requireNonNull(
                geometryValidator,
                "도형 Validator는 null일 수 없습니다."
        );
        this.policy = Objects.requireNonNull(
                policy,
                "도형 정책은 null일 수 없습니다."
        );
        this.geometrySpatialPort = Objects.requireNonNull(
                geometrySpatialPort,
                "공간 검증 포트는 null일 수 없습니다."
        );
        this.geometryFactory = new GeometryFactory();
    }

    /**
     * map_boundary 생성 또는 수정 요청의 Polygon을 검증한다.
     * <p>공통 Polygon 검증을 수행한 뒤, 실제 면적이 최소 면적 기준 이상인지 확인한다.</p>
     *
     * @param polygon GeoJSON Polygon 입력값
     * @return 도형 검증 결과
     */
    public GeometryValidationResult validateMapBoundaryPolygon(GeoJsonPolygon polygon) {
        GeometryValidationResult result = validateCommonPolygon(polygon);

        validateMinimumArea(result);

        return result;
    }

    /**
     * search_area 생성·수정 요청의 Polygon을 검증한다.
     *
     * <p>공통 Polygon 검증과 최소 면적 검증을 수행한 뒤,
     * search_area가 active map_boundary 내부에 완전히 포함되는지 확인한다.</p>
     *
     * @param polygon GeoJSON Polygon 입력값
     * @param activeBoundaryWkt active map_boundary Polygon WKT
     * @return 검증과 정규화가 끝난 도형 결과
     */
    public GeometryValidationResult validateSearchAreaPolygon(
            GeoJsonPolygon polygon,
            String activeBoundaryWkt
    ) {
        if (activeBoundaryWkt == null || activeBoundaryWkt.isBlank()) {
            throw MapBoundaryRequiredException.mapBoundaryRequired("active map_boundary geometry가 필요합니다.");
        }

        GeometryValidationResult result = validateCommonPolygon(polygon);

        validateMinimumArea(result);
        validateInsideActiveBoundary(activeBoundaryWkt, result);

        return result;
    }

    /**
     * split 요청으로 생성될 child Polygon 목록을 검증한다.
     *
     * <p>각 child Polygon의 공통 검증과 최소 면적 검증을 수행한다.</p>
     * <p>이후 모든 child가 parent 내부에 포함되는지, child끼리 면적으로 겹치지 않는지 확인한다.</p>
     *
     * @param parentAreaWkt split 대상 parent search_area Polygon WKT
     * @param childPolygons split child GeoJSON Polygon 목록
     * @return child Polygon별 검증 결과 목록
     */
    public List<GeometryValidationResult> validateSplitChildren(
            String parentAreaWkt,
            List<GeoJsonPolygon> childPolygons
    ) {
        if (parentAreaWkt == null || parentAreaWkt.isBlank()) {
            throw InvalidGeometryException.invalidGeometry("split parent Polygon geometry가 필요합니다.");
        }

        if (childPolygons == null || childPolygons.isEmpty()) {
            throw InvalidGeometryException.invalidGeometry("split child Polygon은 1개 이상이어야 합니다.");
        }

        if (childPolygons.size() < 2) {
            throw InvalidGeometryException.invalidGeometry("split child Polygon은 최소 2개 이상이어야 합니다.");
        }

        List<GeometryValidationResult> results = childPolygons.stream()
                .map(this::validateMapBoundaryPolygon)
                .toList();

        validateAllChildrenInsideParent(parentAreaWkt, results);
        validateChildrenDoNotOverlap(results);

        return results;
    }

    /**
     * map_boundary, search_area, split child에 공통으로 적용되는 Polygon 검증을 수행한다.
     *
     * <p>GeoJSON type과 outer ring을 확인하고, ring을 S2 기준으로 정규화한 뒤
     * JTS Polygon으로 변환한다.</p>
     * <p>JTS 기준의 빈 Polygon, 자기 교차, 0면적 여부까지 확인한다.</p>
     *
     * @param polygon GeoJSON Polygon 입력값
     * @return 공통 검증과 정규화가 끝난 도형 결과
     */
    private GeometryValidationResult validateCommonPolygon(GeoJsonPolygon polygon) {
        validateGeoJsonPolygonType(polygon);

        List<List<BigDecimal>> outerRing = extractOuterRing(polygon);

        List<List<BigDecimal>> canonicalOuterRing =
                geometryValidator.validateAndCanonicalizePolygonRing(outerRing);

        Polygon jtsPolygon = toJtsPolygon(canonicalOuterRing);

        validateJtsPolygon(jtsPolygon);

        return GeometryValidationResult.of(canonicalOuterRing, jtsPolygon);
    }

    /**
     * GeoJSON geometry type이 Polygon인지 검증한다.
     *
     * @param polygon GeoJSON Polygon 입력값
     */
    private void validateGeoJsonPolygonType(GeoJsonPolygon polygon) {
        if (polygon == null) {
            throw InvalidGeometryException.invalidGeometry("GeoJSON Polygon은 null일 수 없습니다.");
        }

        if (!"Polygon".equals(polygon.type())) {
            throw InvalidGeometryException.invalidGeometry("GeoJSON geometry type은 Polygon이어야 합니다.");
        }
    }

    /**
     * GeoJSON Polygon에서 outer ring을 꺼낸다.
     *
     * @param polygon GeoJSON Polygon 입력값
     * @return Polygon outer ring 좌표 목록
     */
    private List<List<BigDecimal>> extractOuterRing(GeoJsonPolygon polygon) {
        List<List<BigDecimal>> outerRing = polygon.outerRing();

        if (outerRing == null) {
            throw InvalidGeometryException.invalidGeometry("GeoJSON Polygon outer ring은 비어 있을 수 없습니다.");
        }

        return outerRing;
    }

    /**
     * canonical outer ring을 JTS Polygon으로 변환한다.
     *
     * @param outerRing canonical outer ring
     * @return JTS Polygon
     */
    private Polygon toJtsPolygon(List<List<BigDecimal>> outerRing) {
        Coordinate[] coordinates = outerRing.stream()
                .map(point -> new Coordinate(
                        point.get(0).doubleValue(),
                        point.get(1).doubleValue()
                ))
                .toArray(Coordinate[]::new);

        LinearRing shell = geometryFactory.createLinearRing(coordinates);
        Polygon polygon = geometryFactory.createPolygon(shell);

        polygon.setSRID(policy.srid());

        return polygon;
    }

    /**
     * JTS 기준으로 Polygon의 기본 유효성을 검사한다.
     *
     * <p>빈 Polygon, 자기 교차, 0면적 Polygon을 거부한다.</p>
     * <p>실제 m² 기준 최소 면적 검증은 PostGIS 기반 {@link #validateMinimumArea(GeometryValidationResult)}에서 수행한다.</p>
     *
     * @param polygon JTS Polygon
     */
    private void validateJtsPolygon(Polygon polygon) {
        if (polygon == null || polygon.isEmpty()) {
            throw InvalidGeometryException.invalidGeometry("Polygon은 비어 있을 수 없습니다.");
        }

        if (!polygon.isValid()) {
            throw InvalidGeometryException.invalidGeometry("Polygon은 자기 교차 없이 유효해야 합니다.");
        }

        if (polygon.getArea() <= 0.0) {
            throw InvalidGeometryException.invalidGeometry("Polygon 면적은 0보다 커야 합니다.");
        }
    }

    /**
     * Polygon의 실제 면적이 S2 최소 면적 이상인지 검증한다.
     *
     * @param result 도형 검증 결과
     */
    private void validateMinimumArea(GeometryValidationResult result) {
        boolean valid = geometrySpatialPort.isAreaAtLeastM2(
                result.wkt(),
                policy.minimumPolygonAreaM2()
        );

        if (!valid) {
            throw InvalidGeometryException.invalidGeometry(
                    "Polygon 면적은 최소 " + policy.minimumPolygonAreaM2() + "㎡ 이상이어야 합니다."
            );
        }
    }

    /**
     * search_area Polygon이 active map_boundary 내부에 완전히 포함되는지 검증한다.
     *
     * <p>경계선에 닿는 경우까지 허용하기 위해 포함 여부는 PostGIS covers 검증에 위임한다.</p>
     *
     * @param activeBoundaryWkt active map_boundary WKT
     * @param result search_area 검증 결과
     */
    private void validateInsideActiveBoundary(
            String activeBoundaryWkt,
            GeometryValidationResult result
    ) {
        boolean covered = geometrySpatialPort.covers(
                activeBoundaryWkt,
                result.wkt()
        );

        if (!covered) {
            throw InvalidGeometryException.invalidGeometry("search_area Polygon은 active map_boundary 내부에 있어야 합니다.");
        }
    }

    /**
     * 모든 split child Polygon이 parent Polygon 내부에 완전히 포함되는지 검증한다.
     *
     * @param parentAreaWkt split 대상 parent Polygon WKT
     * @param children child Polygon 검증 결과 목록
     */
    private void validateAllChildrenInsideParent(
            String parentAreaWkt,
            List<GeometryValidationResult> children
    ) {
        for (GeometryValidationResult child : children) {
            boolean covered = geometrySpatialPort.covers(
                    parentAreaWkt,
                    child.wkt()
            );

            if (!covered) {
                throw InvalidGeometryException.invalidGeometry("split child Polygon은 parent Polygon 내부에 있어야 합니다.");
            }
        }
    }

    /**
     * split child Polygon끼리 면적 기준으로 겹치지 않는지 검증한다.
     *
     * <p>경계선만 맞닿는 경우는 겹침으로 보지 않고, 실제 면적이 겹치는 경우만 거부한다.</p>
     *
     * @param children child Polygon 검증 결과 목록
     */
    private void validateChildrenDoNotOverlap(List<GeometryValidationResult> children) {
        for (int i = 0; i < children.size(); i++) {
            for (int j = i + 1; j < children.size(); j++) {
                boolean overlaps = geometrySpatialPort.overlapsByArea(
                        children.get(i).wkt(),
                        children.get(j).wkt()
                );

                if (overlaps) {
                    throw InvalidGeometryException.invalidGeometry("split child Polygon끼리는 서로 겹칠 수 없습니다.");
                }
            }
        }
    }
}
