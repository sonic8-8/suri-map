package com.surimap.maparea;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.GeometryFixtures;
import com.surimap.maparea.fixture.SearchAreaQueryFixtures;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.maparea.testdouble.SearchAreaQueryMock;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T04A AreaQuery.byOp mock contract 테스트.
 *
 * <p>real DB query 없이 소비 Lane이 OP 기준 area id/status/version/geometry/bbox 계약을 검증할 수 있다.
 */
@DisplayName("L3-T04A AreaQuery.byOp mock contract")
class SearchAreaQueryByOpContractTest {

  private final SearchAreaQueryMock mock = new SearchAreaQueryMock();

  @Test
  @DisplayName("알려진 op1Id는 ACTIVE area 컬렉션을 반환한다")
  void known_opId는_area_컬렉션을_반환한다() {
    SearchAreaCollection result = mock.byOp(BoundaryAreaFixtures.OP1_ID, SearchAreaFilters.empty());

    assertThat(result.areas()).isNotEmpty();
  }

  @Test
  @DisplayName("알 수 없는 opId는 빈 컬렉션을 반환한다")
  void 알_수_없는_opId는_빈_컬렉션을_반환한다() {
    SearchAreaCollection result = mock.byOp(UUID.randomUUID(), SearchAreaFilters.empty());

    assertThat(result.areas()).isEmpty();
  }

  @Test
  @DisplayName("byOp area row는 canonical id/opId/status/version을 포함한다")
  void byOp_area_row는_canonical_필드를_포함한다() {
    SearchAreaCollection result = mock.byOp(BoundaryAreaFixtures.OP1_ID, SearchAreaFilters.empty());
    SearchAreaRow area = result.areas().get(0);

    assertThat(area.id()).isEqualTo(BoundaryAreaFixtures.AREA_ID);
    assertThat(area.opId()).isEqualTo(BoundaryAreaFixtures.OP1_ID);
    assertThat(area.status()).isEqualTo("ACTIVE");
    assertThat(area.version()).isEqualTo(BoundaryAreaFixtures.AREA_CREATED_VERSION);
  }

  @Test
  @DisplayName("byOp area geometry는 canonical GeoJSON Polygon이다")
  void byOp_area_geometry는_canonical_polygon이다() {
    SearchAreaCollection result = mock.byOp(BoundaryAreaFixtures.OP1_ID, SearchAreaFilters.empty());
    SearchAreaRow area = result.areas().get(0);

    assertThat(area.geometry().type()).isEqualTo("Polygon");
    assertThat(area.geometry().coordinates())
        .containsExactly(GeometryFixtures.validSearchAreaRing());
    assertThat(area.bbox()).isEqualTo(GeometryFixtures.AREA_GEOMETRY_BBOX);
  }

  @Test
  @DisplayName("byOp와 byIncident는 같은 area id/status/version/geometry를 공유한다")
  void byOp와_byIncident는_같은_fixture를_공유한다() {
    SearchAreaCollection byOp = mock.byOp(BoundaryAreaFixtures.OP1_ID, SearchAreaFilters.empty());
    SearchAreaCollection byIncident =
        mock.byIncident(BoundaryAreaFixtures.INCIDENT_ID, SearchAreaFilters.empty());

    SearchAreaRow opArea = byOp.areas().get(0);
    SearchAreaRow incidentArea = byIncident.areas().get(0);

    assertThat(opArea.id()).isEqualTo(incidentArea.id());
    assertThat(opArea.status()).isEqualTo(incidentArea.status());
    assertThat(opArea.version()).isEqualTo(incidentArea.version());
    assertThat(opArea.geometry()).isEqualTo(incidentArea.geometry());
    assertThat(opArea.bbox()).isEqualTo(incidentArea.bbox());
  }

  @Test
  @DisplayName("S3-2/S7/S8 소비자는 같은 byOp fixture를 참조한다")
  void 소비자는_같은_byOp_fixture를_참조한다() {
    SearchAreaCollection result = mock.byOp(BoundaryAreaFixtures.OP1_ID, SearchAreaFilters.empty());
    SearchAreaRow fixture = SearchAreaQueryFixtures.activeAreaRow();

    SearchAreaRow area = result.areas().get(0);
    assertThat(area.id()).isEqualTo(fixture.id());
    assertThat(area.status()).isEqualTo(fixture.status());
    assertThat(area.version()).isEqualTo(fixture.version());
    assertThat(area.geometry()).isEqualTo(fixture.geometry());
  }
}
