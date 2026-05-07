package com.surimap.marker.domain;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.testdouble.SearchAreaQueryMock;
import com.surimap.marker.domain.exception.InvalidGeometryException;
import com.surimap.marker.domain.port.MarkerLocationValidator;
import com.surimap.marker.domain.service.MarkerLocationValidatorImpl;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * 마커 위치 검증 테스트.
 *
 * <p>MarkerLocationValidatorImpl 구현체와 연결하여 green 전환. 구현체를 작성하면 green으로 전환된다.
 *
 * <p>SC-06 harness red test 기준: - marker Point가 overall_search_area 밖이면 400 invalid_geometry -
 * Point type이 아니면 400 invalid_geometry - null/NaN 좌표 거부 - precision 6자리 정규화 후 재검증
 *
 * @see docs/spec/specs/S5.json
 * @see docs/spec/boundaries.md
 */
@DisplayName("마커 위치 검증 red test")
class MarkerLocationValidatorRedTest {

  private static final GeometryFactory GF =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

  private final SearchAreaQuery searchAreaQuery = new SearchAreaQueryMock();

  private final MarkerLocationValidator validator =
      new MarkerLocationValidatorImpl(searchAreaQuery);

  // ══════════════════════════════════════════════════════
  // 정상 케이스
  // ══════════════════════════════════════════════════════

  @Nested
  @DisplayName("정상 좌표")
  class ValidCases {

    @Test
    @DisplayName("overall_search_area 내 정상 좌표 → 검증 통과")
    void validPoint_insideBoundary_passes() {
      assertDoesNotThrow(() -> validator.validate(INCIDENT_ID, VALID_MARKER_POINT));
    }

    @Test
    @DisplayName("boundary 경계선 위 좌표 → 검증 통과")
    void pointOnBoundaryEdge_passes() {
      Point edgePoint = GF.createPoint(new Coordinate(126.948000, 37.570000));
      assertDoesNotThrow(() -> validator.validate(INCIDENT_ID, edgePoint));
    }
  }

  // ══════════════════════════════════════════════════════
  // 실패 케이스
  // ══════════════════════════════════════════════════════

  @Nested
  @DisplayName("좌표 검증 실패")
  class InvalidCases {

    @Test
    @DisplayName("overall_search_area 밖 좌표 → invalid_geometry")
    void outsideBoundary_rejected() {
      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, OUTSIDE_ENVELOPE))
          .isInstanceOf(InvalidGeometryException.class);
    }

    @Test
    @DisplayName("lon/lat 뒤바뀐 좌표 → invalid_geometry")
    void swappedLatLon_rejected() {
      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, LATLON_SWAPPED))
          .isInstanceOf(InvalidGeometryException.class);
    }

    @Test
    @DisplayName("NaN 좌표 → invalid_geometry")
    void nanCoordinate_rejected() {
      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, NAN_POINT))
          .isInstanceOf(InvalidGeometryException.class);
    }

    @Test
    @DisplayName("빈 Point → invalid_geometry")
    void emptyPoint_rejected() {
      Point emptyPoint = GF.createPoint();

      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, emptyPoint))
          .isInstanceOf(InvalidGeometryException.class);
    }

    @Test
    @DisplayName("null 좌표 → invalid_geometry")
    void nullPoint_rejected() {
      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, null))
          .isInstanceOf(InvalidGeometryException.class);
    }

    @Test
    @DisplayName("경도 범위 초과 (>180) → invalid_geometry")
    void longitudeOutOfRange_rejected() {
      Point outOfRange = GF.createPoint(new Coordinate(181.0, 37.571200));
      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, outOfRange))
          .isInstanceOf(InvalidGeometryException.class);
    }

    @Test
    @DisplayName("위도 범위 초과 (>90) → invalid_geometry")
    void latitudeOutOfRange_rejected() {
      Point outOfRange = GF.createPoint(new Coordinate(126.956500, 91.0));
      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, outOfRange))
          .isInstanceOf(InvalidGeometryException.class);
    }

    @Test
    @DisplayName("EPSG:4326이 아닌 SRID → invalid_geometry")
    void sridMismatch_rejected() {
      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, SRID_MISMATCH_POINT))
          .isInstanceOf(InvalidGeometryException.class);
    }
  }

  // ══════════════════════════════════════════════════════
  // overall_search_area 없음
  // ══════════════════════════════════════════════════════

  @Nested
  @DisplayName("overall_search_area 부재")
  class NoBoundary {

    @Test
    @DisplayName("active overall_search_area 없으면 → invalid_geometry")
    void noBoundary_rejected() {
      assertThatThrownBy(() -> validator.validate(UUID.randomUUID(), VALID_MARKER_POINT))
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
    @DisplayName("7자리 좌표 → 6자리 canonical 정규화 후 내부이면 통과")
    void precisionOver6dp_normalizedAndPasses() {
      assertDoesNotThrow(() -> validator.validate(INCIDENT_ID, PRECISION_OVER_6DP));
    }
  }
}
