package com.surimap.maparea.geometry.validation;

import com.surimap.maparea.geometry.exception.InvalidGeometryException;
import com.surimap.maparea.geometry.policy.GeometryPolicy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * S2 Polygon 입력값을 저장 전에 검사하고 정규화하는 Validator.
 *
 * <p>이 클래스는 DB나 PostGIS 없이 확인할 수 있는 1차 검증만 담당한다.</p>
 * <p>좌표 구조, null 여부, EPSG:4326 경도·위도 범위, S2 하네스 bbox 포함 여부,
 * 소수점 6자리 정규화, 연속 중복 좌표 제거, ring 닫힘 여부를 검사한다.</p>
 * <p>자기 교차, 실제 면적 400㎡ 이상 여부, active map_boundary 내부 포함 여부,
 * split child 간 겹침 여부는 PostGIS 기반 검증 또는 상위 서비스에서 처리한다.</p>
 */
public class GeometryValidator {

    /** S2 도형 검증 정책 */
    private final GeometryPolicy policy;

    /**
     * GeometryValidator를 생성한다.
     *
     * @param policy 도형 검증 정책
     */
    public GeometryValidator(GeometryPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "도형 검증 정책은 null일 수 없습니다.");
    }

    /**
     * Polygon outer ring을 검증하고 정규화한다.
     *
     * <p>각 좌표를 소수점 6자리로 맞춘 뒤, 연속 중복 좌표를 제거한다.</p>
     * <p>정규화된 ring의 좌표 개수, ring 닫힘 여부, EPSG:4326 범위,
     * S2 하네스 bbox 포함 여부를 검사한다.</p>
     *
     * @param ring GeoJSON Polygon의 outer ring 좌표 목록
     * @return 정규화와 1차 검증이 끝난 Polygon outer ring
     */
    public List<List<BigDecimal>> validateAndCanonicalizePolygonRing(List<List<BigDecimal>> ring) {
        validateRingNotEmpty(ring);

        List<List<BigDecimal>> canonicalRing = canonicalizeRing(ring);
        List<List<BigDecimal>> deduplicatedRing = removeConsecutiveDuplicatePoints(canonicalRing);

        validateRingHasEnoughPoints(deduplicatedRing);
        validateRingClosed(deduplicatedRing);
        validateAllCanonicalCoordinates(deduplicatedRing);

        return deduplicatedRing;
    }

    /**
     * Polygon outer ring을 검증한다.
     *
     * <p>정규화된 결과가 필요하지 않고, 입력값의 유효 여부만 확인할 때 사용한다.</p>
     *
     * @param ring GeoJSON Polygon의 outer ring 좌표 목록
     */
    public void validatePolygonRing(List<List<BigDecimal>> ring) {
        validateAndCanonicalizePolygonRing(ring);
    }

    /**
     * 단일 좌표를 검증하고 S2 기준에 맞게 정규화한다.
     *
     * <p>좌표는 GeoJSON 기준인 [경도, 위도] 순서여야 한다.</p>
     * <p>경도와 위도를 소수점 6자리로 맞춘 뒤, EPSG:4326 범위와
     * S2 하네스 bbox 포함 여부를 검사한다.</p>
     *
     * @param point [경도, 위도] 좌표
     * @return 정규화된 [경도, 위도] 좌표
     */
    public List<BigDecimal> validateAndCanonicalizePoint(List<BigDecimal> point) {
        validatePointShape(point);

        BigDecimal lon = point.get(0);
        BigDecimal lat = point.get(1);

        validateCoordinateNotNull(lon, lat);

        BigDecimal canonicalLon = policy.canonicalizeCoordinate(lon);
        BigDecimal canonicalLat = policy.canonicalizeCoordinate(lat);

        validateLonLatRange(canonicalLon, canonicalLat);
        validateFixtureBbox(canonicalLon, canonicalLat);

        return List.of(canonicalLon, canonicalLat);
    }

    /**
     * 단일 좌표가 [경도, 위도] 구조이고 정책 범위 안에 있는지 검증한다.
     *<p>정규화된 결과가 필요하지 않고, 좌표의 유효 여부만 확인할 때 사용한다.</p>
     *
     * @param point [경도, 위도] 좌표
     */
    public void validatePoint(List<BigDecimal> point) {
        validateAndCanonicalizePoint(point);
    }

    /**
     * Polygon ring이 null이 아니고 비어 있지 않은지 검사한다.
     *
     * @param ring Polygon outer ring 좌표 목록
     */
    private void validateRingNotEmpty(List<List<BigDecimal>> ring) {
        if (ring == null || ring.isEmpty()) {
            throw InvalidGeometryException.invalidGeometry("Polygon ring은 비어 있을 수 없습니다.");
        }
    }

    /**
     * Polygon ring이 최소 4개 좌표를 가지는지 검증한다.
     *
     * @param ring Polygon outer ring 좌표 목록
     */
    private void validateRingHasEnoughPoints(List<List<BigDecimal>> ring) {
        if (ring.size() < 4) {
            throw InvalidGeometryException.invalidGeometry("Polygon ring은 최소 4개의 좌표가 필요합니다.");
        }
    }

    /**
     * Polygon ring의 첫 좌표와 마지막 좌표가 같은지 검증한다.
     *
     * @param ring Polygon outer ring 좌표 목록
     */
    private void validateRingClosed(List<List<BigDecimal>> ring) {
        List<BigDecimal> first = ring.get(0);
        List<BigDecimal> last = ring.get(ring.size() - 1);

        if (!isSamePoint(first, last)) {
            throw InvalidGeometryException.invalidGeometry("Polygon ring은 정규화 이후 첫 좌표와 마지막 좌표가 같아야 합니다.");
        }
    }

    /**
     * ring 안의 모든 좌표가 허용 범위 안에 있는지 검사한다.
     *
     * <p>각 좌표는 EPSG:4326 경도·위도 범위 안에 있어야 하고,
     * S2 하네스 bbox 내부에 포함되어야 한다.</p>
     *
     * @param ring 정규화된 Polygon outer ring 좌표 목록
     */
    private void validateAllCanonicalCoordinates(List<List<BigDecimal>> ring) {
        for (List<BigDecimal> point : ring) {
            BigDecimal lon = point.get(0);
            BigDecimal lat = point.get(1);

            validateLonLatRange(lon, lat);
            validateFixtureBbox(lon, lat);
        }
    }

    /**
     * ring 안의 모든 좌표를 S2 기준에 맞게 정규화한다.
     *
     * <p>각 좌표는 [경도, 위도] 구조 검증, null 검증,
     * 소수점 6자리 정규화, 범위 검증을 거친다.</p>
     *
     * @param ring Polygon outer ring 좌표 목록
     * @return 정규화된 Polygon outer ring 좌표 목록
     */
    private List<List<BigDecimal>> canonicalizeRing(List<List<BigDecimal>> ring) {
        List<List<BigDecimal>> canonicalRing = new ArrayList<>();

        for (List<BigDecimal> point : ring) {
            canonicalRing.add(validateAndCanonicalizePoint(point));
        }

        return canonicalRing;
    }

    /**
     * 연속으로 중복된 좌표를 제거한다.
     *
     * <p>중간에 같은 좌표가 연속으로 반복되면 하나만 남긴다.</p>
     * <p>단, 첫 좌표와 마지막 좌표가 같은 ring 닫힘 좌표는 제거하지 않는다.</p>
     *
     * @param ring 정규화된 Polygon outer ring 좌표 목록
     * @return 연속 중복 좌표가 제거된 Polygon outer ring 좌표 목록
     */
    private List<List<BigDecimal>> removeConsecutiveDuplicatePoints(List<List<BigDecimal>> ring) {
        if (!policy.shouldRemoveConsecutiveDuplicatePoints()) {
            return ring;
        }

        List<List<BigDecimal>> result = new ArrayList<>();

        for (int i = 0; i < ring.size(); i++) {
            List<BigDecimal> current = ring.get(i);

            if (i == 0) {
                result.add(current);
                continue;
            }

            List<BigDecimal> previous = result.get(result.size() - 1);

            boolean isLastPoint = i == ring.size() - 1;
            boolean closesRing = isSamePoint(current, result.get(0));

            if (isSamePoint(previous, current) && !(isLastPoint && closesRing)) {
                continue;
            }

            result.add(current);
        }

        return result;
    }

    /**
     * 좌표가 [경도, 위도] 형태인지 검증한다.
     *
     * @param point 검증할 좌표
     */
    private void validatePointShape(List<BigDecimal> point) {
        if (point == null || point.size() != 2) {
            throw InvalidGeometryException.invalidGeometry("좌표는 [경도, 위도] 형식이어야 합니다.");
        }
    }

    /**
     * 경도와 위도가 null이 아닌지 검증한다.
     *
     * @param lon 경도
     * @param lat 위도
     */
    private void validateCoordinateNotNull(BigDecimal lon, BigDecimal lat) {
        if (lon == null || lat == null) {
            throw InvalidGeometryException.invalidGeometry("경도와 위도는 null일 수 없습니다.");
        }
    }

    /**
     * 경도와 위도가 EPSG:4326에서 표현 가능한 범위인지 검사한다.
     * <p>경도는 -180 이상 180 이하, 위도는 -90 이상 90 이하이어야 한다.</p>
     *
     * @param lon 경도
     * @param lat 위도
     */
    private void validateLonLatRange(BigDecimal lon, BigDecimal lat) {
        if (!GeometryPolicy.isValidLongitude(lon) || !GeometryPolicy.isValidLatitude(lat)) {
            throw InvalidGeometryException.invalidGeometry("좌표가 EPSG:4326 유효 범위를 벗어났습니다.");
        }
    }

    /**
     * 좌표가 S2 하네스 bbox 내부에 있는지 검증한다.
     *
     * @param lon 경도
     * @param lat 위도
     */
    private void validateFixtureBbox(BigDecimal lon, BigDecimal lat) {
        if (!policy.containsLonLat(lon, lat)) {
            throw InvalidGeometryException.invalidGeometry("좌표가 S2 하네스 bbox 범위를 벗어났습니다.");
        }
    }

    /**
     * 두 좌표가 같은 좌표인지 확인한다.
     *
     * @param left 왼쪽 좌표
     * @param right 오른쪽 좌표
     * @return 두 좌표의 경도와 위도가 같으면 true
     */
    private boolean isSamePoint(List<BigDecimal> left, List<BigDecimal> right) {
        if (left == null || right == null || left.size() != 2 || right.size() != 2) {
            return false;
        }

        BigDecimal leftLon = left.get(0);
        BigDecimal leftLat = left.get(1);
        BigDecimal rightLon = right.get(0);
        BigDecimal rightLat = right.get(1);

        if (leftLon == null || leftLat == null || rightLon == null || rightLat == null) {
            return false;
        }

        return leftLon.compareTo(rightLon) == 0
                && leftLat.compareTo(rightLat) == 0;
    }
}
