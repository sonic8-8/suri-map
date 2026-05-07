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
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.VALID_MARKER_POINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.surimap.maparea.testdouble.SearchAreaQueryMock;
import com.surimap.marker.domain.exception.InvalidGeometryException;
import com.surimap.marker.domain.validation.MarkerLocationValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/** L5-T03A marker.location과 SearchAreaQuery.overallOf 연결 red test. */
@DisplayName("L5-T03A marker.location 검증 red test")
class MarkerLocationValidatorRedTest {

  private static final GeometryFactory GF =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

  private final MarkerLocationValidator validator =
      new MarkerLocationValidator(new SearchAreaQueryMock());

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
      assertThat(VALID_MARKER_POINT.getX()).isEqualTo(126.956500);
      assertThat(VALID_MARKER_POINT.getY()).isEqualTo(37.571200);
      assertThat(OUTSIDE_ENVELOPE.getX()).isEqualTo(127.200000);
      assertThat(OUTSIDE_ENVELOPE.getY()).isEqualTo(37.571200);
      assertThat(LAT_LON_SWAPPED.getX()).isEqualTo(37.571200);
      assertThat(LAT_LON_SWAPPED.getY()).isEqualTo(126.956500);
      assertThat(PRECISION_OVER_6DP.getX()).isEqualTo(126.9565007);
      assertThat(PRECISION_OVER_6DP.getY()).isEqualTo(37.5712007);
    }

    @Test
    @DisplayName("하네스 envelope를 보존한다")
    void harness_envelope를_보존한다() {
      assertThat(ENVELOPE_MIN_LON).isEqualTo(126.900000);
      assertThat(ENVELOPE_MIN_LAT).isEqualTo(37.500000);
      assertThat(ENVELOPE_MAX_LON).isEqualTo(127.080000);
      assertThat(ENVELOPE_MAX_LAT).isEqualTo(37.620000);
    }
  }

  @Nested
  @DisplayName("valid marker.location")
  class ValidLocation {

    @Test
    @DisplayName("overall_search_area 내부 Point는 통과한다")
    void overall_search_area_내부_point는_통과한다() {
      assertDoesNotThrow(() -> validator.validate(INCIDENT_ID, VALID_MARKER_POINT));
    }

    @Test
    @DisplayName("7자리 좌표는 6자리 canonical coordinate로 정규화한 뒤 통과한다")
    void precision_over_6dp는_정규화_후_통과한다() {
      assertDoesNotThrow(() -> validator.validate(INCIDENT_ID, PRECISION_OVER_6DP));
    }
  }

  @Nested
  @DisplayName("invalid marker.location")
  class InvalidLocation {

    @Test
    @DisplayName("overall_search_area 밖 좌표는 invalid_geometry다")
    void outside_overall_search_area는_invalid_geometry다() {
      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, OUTSIDE_ENVELOPE))
          .isInstanceOfSatisfying(
              InvalidGeometryException.class,
              ex -> assertThat(ex.errorCode()).isEqualTo("invalid_geometry"));
    }

    @Test
    @DisplayName("lat/lon 순서가 뒤집힌 좌표는 invalid_geometry다")
    void lat_lon_순서가_뒤집힌_좌표는_invalid_geometry다() {
      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, LAT_LON_SWAPPED))
          .isInstanceOfSatisfying(
              InvalidGeometryException.class,
              ex -> assertThat(ex.errorCode()).isEqualTo("invalid_geometry"));
    }

    @Test
    @DisplayName("NaN 좌표는 invalid_geometry다")
    void nan_coordinate는_invalid_geometry다() {
      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, NAN_POINT))
          .isInstanceOfSatisfying(
              InvalidGeometryException.class,
              ex -> assertThat(ex.errorCode()).isEqualTo("invalid_geometry"));
    }

    @Test
    @DisplayName("null Point는 invalid_geometry다")
    void null_point는_invalid_geometry다() {
      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, null))
          .isInstanceOfSatisfying(
              InvalidGeometryException.class,
              ex -> assertThat(ex.errorCode()).isEqualTo("invalid_geometry"));
    }

    @Test
    @DisplayName("경도 범위 초과 좌표는 invalid_geometry다")
    void longitude_range_초과는_invalid_geometry다() {
      Point outOfRange = GF.createPoint(new Coordinate(181.0, 37.571200));

      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, outOfRange))
          .isInstanceOfSatisfying(
              InvalidGeometryException.class,
              ex -> assertThat(ex.errorCode()).isEqualTo("invalid_geometry"));
    }

    @Test
    @DisplayName("위도 범위 초과 좌표는 invalid_geometry다")
    void latitude_range_초과는_invalid_geometry다() {
      Point outOfRange = GF.createPoint(new Coordinate(126.956500, 91.0));

      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, outOfRange))
          .isInstanceOfSatisfying(
              InvalidGeometryException.class,
              ex -> assertThat(ex.errorCode()).isEqualTo("invalid_geometry"));
    }

    @Test
    @DisplayName("active overall_search_area가 없으면 invalid_geometry다")
    void active_overall_search_area가_없으면_invalid_geometry다() {
      assertThatThrownBy(() -> validator.validate(java.util.UUID.randomUUID(), VALID_MARKER_POINT))
          .isInstanceOfSatisfying(
              InvalidGeometryException.class,
              ex -> assertThat(ex.errorCode()).isEqualTo("invalid_geometry"));
    }
  }
}
