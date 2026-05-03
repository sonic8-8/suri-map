package com.surimap.marker.domain;

import com.surimap.marker.domain.exception.InvalidGeometryException;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.domain.port.MapBoundaryQueryPort;
import com.surimap.marker.domain.port.MarkerLocationValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.Optional;
import java.util.UUID;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 마커 위치 검증 red test.
 *
 * 이 테스트는 MarkerLocationValidator 구현체가 없으므로 전부 실패한다(red).
 * 구현체를 작성하면 green으로 전환된다.
 *
 * SC-06 harness red test 기준:
 * - marker Point가 map_boundary 밖이면 400 invalid_geometry
 * - Point type이 아니면 400 invalid_geometry
 * - null/NaN 좌표 거부
 * - precision 6자리 정규화 후 재검증
 *
 * @see docs/contracts/L5-05-geometry-spec.md
 */
@DisplayName("마커 위치 검증 red test")
class MarkerLocationValidatorRedTest {

    private static final GeometryFactory GF =
            new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

    /**
     * 테스트용 stub: boundary 내부 포함 여부만 판정한다.
     * 실제 구현은 PostGIS ST_Contains를 사용한다.
     */
    private final MapBoundaryQueryPort stubBoundaryQuery =
            incidentId -> Optional.of(HARNESS_BOUNDARY);

    /** boundary가 없는 stub */
    private final MapBoundaryQueryPort emptyBoundaryQuery =
            incidentId -> Optional.empty();

    // ── TODO: 구현체 주입 후 이 줄만 교체하면 green 전환 ──
    // private final MarkerLocationValidator validator = new MarkerLocationValidatorImpl(stubBoundaryQuery);
    private final MarkerLocationValidator validator = null; // RED: 구현체 없음

    // ══════════════════════════════════════════════════════
    // 정상 케이스
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("정상 좌표")
    class ValidCases {

        @Test
        @DisplayName("map_boundary 내 정상 좌표 → 검증 통과")
        void validPoint_insideBoundary_passes() {
            assertDoesNotThrow(() ->
                    validator.validate(INCIDENT_ID, VALID_MARKER_POINT));
        }

        @Test
        @DisplayName("boundary 경계선 위 좌표 → 검증 통과")
        void pointOnBoundaryEdge_passes() {
            Point edgePoint = GF.createPoint(new Coordinate(126.948000, 37.570000));
            assertDoesNotThrow(() ->
                    validator.validate(INCIDENT_ID, edgePoint));
        }
    }

    // ══════════════════════════════════════════════════════
    // 실패 케이스
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("좌표 검증 실패")
    class InvalidCases {

        @Test
        @DisplayName("map_boundary 밖 좌표 → invalid_geometry")
        void outsideBoundary_rejected() {
            assertThatThrownBy(() ->
                    validator.validate(INCIDENT_ID, OUTSIDE_ENVELOPE))
                    .isInstanceOf(InvalidGeometryException.class);
        }

        @Test
        @DisplayName("lon/lat 뒤바뀐 좌표 → invalid_geometry")
        void swappedLatLon_rejected() {
            assertThatThrownBy(() ->
                    validator.validate(INCIDENT_ID, LATLON_SWAPPED))
                    .isInstanceOf(InvalidGeometryException.class);
        }

        @Test
        @DisplayName("NaN 좌표 → invalid_geometry")
        void nanCoordinate_rejected() {
            assertThatThrownBy(() ->
                    validator.validate(INCIDENT_ID, NAN_POINT))
                    .isInstanceOf(InvalidGeometryException.class);
        }

        @Test
        @DisplayName("null 좌표 → invalid_geometry")
        void nullPoint_rejected() {
            assertThatThrownBy(() ->
                    validator.validate(INCIDENT_ID, null))
                    .isInstanceOf(InvalidGeometryException.class);
        }

        @Test
        @DisplayName("경도 범위 초과 (>180) → invalid_geometry")
        void longitudeOutOfRange_rejected() {
            Point outOfRange = GF.createPoint(new Coordinate(181.0, 37.571200));
            assertThatThrownBy(() ->
                    validator.validate(INCIDENT_ID, outOfRange))
                    .isInstanceOf(InvalidGeometryException.class);
        }

        @Test
        @DisplayName("위도 범위 초과 (>90) → invalid_geometry")
        void latitudeOutOfRange_rejected() {
            Point outOfRange = GF.createPoint(new Coordinate(126.956500, 91.0));
            assertThatThrownBy(() ->
                    validator.validate(INCIDENT_ID, outOfRange))
                    .isInstanceOf(InvalidGeometryException.class);
        }
    }

    // ══════════════════════════════════════════════════════
    // Boundary 없음
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("map_boundary 부재")
    class NoBoundary {

        // boundary가 없는 stub 사용
        // private final MarkerLocationValidator noBoundaryValidator =
        //         new MarkerLocationValidatorImpl(emptyBoundaryQuery);
        private final MarkerLocationValidator noBoundaryValidator = null; // RED

        @Test
        @DisplayName("active boundary 없으면 → invalid_geometry")
        void noBoundary_rejected() {
            assertThatThrownBy(() ->
                    noBoundaryValidator.validate(INCIDENT_ID, VALID_MARKER_POINT))
                    .isInstanceOf(InvalidGeometryException.class);
        }
    }

    // ══════════════════════════════════════════════════════
    // Precision 정규화
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("precision 정규화")
    class PrecisionNormalization {

        @Test
        @DisplayName("7자리 좌표 → 6자리 truncate 후 boundary 내부이면 통과")
        void precisionOver6dp_normalizedAndPasses() {
            // 126.9565007 → 126.956500, 37.5712007 → 37.571200
            // 정규화 후 VALID_MARKER_POINT와 동일 → boundary 내부
            assertDoesNotThrow(() ->
                    validator.validate(INCIDENT_ID, PRECISION_OVER_6DP));
        }
    }
}
