package com.surimap.marker.domain;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.ACCOUNT_ALIAS;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.ENVELOPE_MAX_LAT;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.ENVELOPE_MAX_LON;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.ENVELOPE_MIN_LAT;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.ENVELOPE_MIN_LON;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ALIAS;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.LAT_LON_SWAPPED;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.MARKER_ALIAS;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.NAN_POINT;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ALIAS;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OUTSIDE_ENVELOPE;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.POLICE_PHONE_ALIAS;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.PRECISION_OVER_6DP;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.SRID_MISMATCH_POINT;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.VALID_MARKER_POINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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

/** L5-T03A marker.location 좌표 자체 검증 test. */
@DisplayName("L5-T03A marker.location 검증 test")
class MarkerLocationValidatorRedTest {

  private static final GeometryFactory GF =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

  private final MarkerLocationValidator validator =
      new MarkerLocationValidatorImpl();

  @Nested
  @DisplayName("fixture exactness")
  class FixtureExactness {

    @Test
    @DisplayName("SC-06 marker context fixture alias를 보존한다")
    void sc06_marker_context_fixture_alias를_보존한다() {
      assertThat(INCIDENT_ALIAS).isEqualTo("inc-precinct-first-001");
      assertThat(OP1_ALIAS).isEqualTo("op-precinct-001-op1");
      assertThat(POLICE_PHONE_ALIAS).isEqualTo("dev-precinct-phone-01");
      assertThat(ACCOUNT_ALIAS).isEqualTo("acct-precinct-team");
      assertThat(MARKER_ALIAS).isEqualTo("mk-precinct-clue-001");
    }

    @Test
    @DisplayName("하네스 marker 좌표 fixture를 문자열 단위로 고정한다")
    void harness_marker_coordinate_fixture를_고정한다() {
      assertThat(VALID_MARKER_POINT.getX()).isEqualTo(126.913400);
      assertThat(VALID_MARKER_POINT.getY()).isEqualTo(35.163100);
      assertThat(OUTSIDE_ENVELOPE.getX()).isEqualTo(127.200000);
      assertThat(OUTSIDE_ENVELOPE.getY()).isEqualTo(35.163100);
      assertThat(LAT_LON_SWAPPED.getX()).isEqualTo(35.163100);
      assertThat(LAT_LON_SWAPPED.getY()).isEqualTo(126.913400);
      assertThat(PRECISION_OVER_6DP.getX()).isEqualTo(126.9134007);
      assertThat(PRECISION_OVER_6DP.getY()).isEqualTo(35.1631007);
    }

    @Test
    @DisplayName("fixture envelope를 고정한다")
    void fixture_envelope를_고정한다() {
      assertThat(ENVELOPE_MIN_LON).isEqualTo(126.647507);
      assertThat(ENVELOPE_MIN_LAT).isEqualTo(35.052595);
      assertThat(ENVELOPE_MAX_LON).isEqualTo(127.017482);
      assertThat(ENVELOPE_MAX_LAT).isEqualTo(35.256837);
    }
  }

  @Nested
  @DisplayName("valid marker.location")
  class ValidCases {

    @Test
    @DisplayName("overall_search_area 내 정상 좌표는 통과한다")
    void overall_search_area_내_정상_좌표는_통과한다() {
      assertDoesNotThrow(() -> validator.validate(INCIDENT_ID, VALID_MARKER_POINT));
    }

    @Test
    @DisplayName("boundary 경계선 위 좌표는 통과한다")
    void boundary_경계선_위_좌표는_통과한다() {
      Point edgePoint = GF.createPoint(new Coordinate(126.904000, 35.162000));

      assertDoesNotThrow(() -> validator.validate(INCIDENT_ID, edgePoint));
    }

    @Test
    @DisplayName("7자리 좌표도 좌표 범위가 유효하면 통과한다")
    void precision_7자리_좌표도_좌표_범위가_유효하면_통과한다() {
      assertDoesNotThrow(() -> validator.validate(INCIDENT_ID, PRECISION_OVER_6DP));
    }

    @Test
    @DisplayName("overall_search_area 밖 좌표도 좌표 자체가 유효하면 통과한다")
    void overall_search_area_밖_좌표도_좌표_자체가_유효하면_통과한다() {
      assertDoesNotThrow(() -> validator.validate(INCIDENT_ID, OUTSIDE_ENVELOPE));
    }
  }

  @Nested
  @DisplayName("invalid marker.location")
  class InvalidCases {

    @Test
    @DisplayName("lat/lon 순서가 뒤집힌 좌표는 invalid_geometry다")
    void lat_lon_순서가_뒤집힌_좌표는_invalid_geometry다() {
      assertInvalidGeometry(() -> validator.validate(INCIDENT_ID, LAT_LON_SWAPPED));
    }

    @Test
    @DisplayName("NaN 좌표는 invalid_geometry다")
    void nan_좌표는_invalid_geometry다() {
      assertInvalidGeometry(() -> validator.validate(INCIDENT_ID, NAN_POINT));
    }

    @Test
    @DisplayName("empty Point는 invalid_geometry다")
    void empty_point는_invalid_geometry다() {
      Point emptyPoint = GF.createPoint();

      assertInvalidGeometry(() -> validator.validate(INCIDENT_ID, emptyPoint));
    }

    @Test
    @DisplayName("null Point는 invalid_geometry다")
    void null_point는_invalid_geometry다() {
      assertInvalidGeometry(() -> validator.validate(INCIDENT_ID, null));
    }

    @Test
    @DisplayName("경도 범위 초과 좌표는 invalid_geometry다")
    void 경도_범위_초과_좌표는_invalid_geometry다() {
      Point outOfRange = GF.createPoint(new Coordinate(181.0, 35.163100));

      assertInvalidGeometry(() -> validator.validate(INCIDENT_ID, outOfRange));
    }

    @Test
    @DisplayName("위도 범위 초과 좌표는 invalid_geometry다")
    void 위도_범위_초과_좌표는_invalid_geometry다() {
      Point outOfRange = GF.createPoint(new Coordinate(126.913400, 91.0));

      assertInvalidGeometry(() -> validator.validate(INCIDENT_ID, outOfRange));
    }

    @Test
    @DisplayName("EPSG:4326이 아닌 SRID는 invalid_geometry다")
    void epsg4326이_아닌_srid는_invalid_geometry다() {
      assertInvalidGeometry(() -> validator.validate(INCIDENT_ID, SRID_MISMATCH_POINT));
    }

    @Test
    @DisplayName("active overall_search_area가 없어도 초동 마커 좌표는 통과한다")
    void active_overall_search_area가_없어도_초동_마커_좌표는_통과한다() {
      assertDoesNotThrow(() -> validator.validate(UUID.randomUUID(), VALID_MARKER_POINT));
    }
  }

  private static void assertInvalidGeometry(ThrowingRunnable runnable) {
    assertThatThrownBy(runnable::run)
        .isInstanceOfSatisfying(
            InvalidGeometryException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo("invalid_geometry"));
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run();
  }
}
