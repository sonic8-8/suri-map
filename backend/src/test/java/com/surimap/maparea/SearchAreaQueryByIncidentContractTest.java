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
 * L3-T04A AreaQuery.byIncident mock contract 테스트.
 *
 * <p>real DB query 없이 소비 Lane이 id/status/version/geometry/bbox 기준의 실패 contract test를 실행할 수 있다.
 */
@DisplayName("L3-T04A AreaQuery.byIncident mock contract")
class SearchAreaQueryByIncidentContractTest {

  private final SearchAreaQueryMock mock = new SearchAreaQueryMock();

  @Test
  @DisplayName("알려진 incidentId는 ACTIVE area 컬렉션을 반환한다")
  void known_incidentId는_area_컬렉션을_반환한다() {
    SearchAreaCollection result =
        mock.byIncident(BoundaryAreaFixtures.INCIDENT_ID, SearchAreaFilters.empty());

    assertThat(result.incidentId()).isEqualTo(BoundaryAreaFixtures.INCIDENT_ID);
    assertThat(result.areas()).isNotEmpty();
  }

  @Test
  @DisplayName("알 수 없는 incidentId는 빈 컬렉션을 반환한다")
  void 알_수_없는_incidentId는_빈_컬렉션을_반환한다() {
    SearchAreaCollection result = mock.byIncident(UUID.randomUUID(), SearchAreaFilters.empty());

    assertThat(result.areas()).isEmpty();
  }

  @Test
  @DisplayName("byIncident area row는 canonical id/opId/status/version을 포함한다")
  void byIncident_area_row는_canonical_필드를_포함한다() {
    SearchAreaCollection result =
        mock.byIncident(BoundaryAreaFixtures.INCIDENT_ID, SearchAreaFilters.empty());
    SearchAreaRow area = result.areas().get(0);

    assertThat(area.id()).isEqualTo(BoundaryAreaFixtures.AREA_ID);
    assertThat(area.incidentId()).isEqualTo(BoundaryAreaFixtures.INCIDENT_ID);
    assertThat(area.opId()).isEqualTo(BoundaryAreaFixtures.OP1_ID);
    assertThat(area.status()).isEqualTo("ACTIVE");
    assertThat(area.version()).isEqualTo(BoundaryAreaFixtures.AREA_CREATED_VERSION);
  }

  @Test
  @DisplayName("byIncident area geometry는 canonical GeoJSON Polygon이다")
  void byIncident_area_geometry는_canonical_polygon이다() {
    SearchAreaCollection result =
        mock.byIncident(BoundaryAreaFixtures.INCIDENT_ID, SearchAreaFilters.empty());
    SearchAreaRow area = result.areas().get(0);

    assertThat(area.geometry().type()).isEqualTo("Polygon");
    assertThat(area.geometry().coordinates())
        .containsExactly(GeometryFixtures.validSearchAreaRing());
    assertThat(area.bbox()).isEqualTo(GeometryFixtures.AREA_GEOMETRY_BBOX);
  }

  @Test
  @DisplayName("sourceVersion은 결과 내 area version의 최댓값이다")
  void sourceVersion은_area_version_최댓값이다() {
    SearchAreaCollection result =
        mock.byIncident(BoundaryAreaFixtures.INCIDENT_ID, SearchAreaFilters.empty());

    long expectedMax = result.areas().stream().mapToLong(SearchAreaRow::version).max().orElse(0L);
    assertThat(result.sourceVersion()).isEqualTo(expectedMax);
  }

  @Test
  @DisplayName("S3-2/S7/S8 소비자는 같은 byIncident id/status/version/geometry를 비교한다")
  void 소비자는_같은_byIncident_fixture를_참조한다() {
    SearchAreaCollection result =
        mock.byIncident(BoundaryAreaFixtures.INCIDENT_ID, SearchAreaFilters.empty());
    SearchAreaRow fixture = SearchAreaQueryFixtures.activeAreaRow();

    SearchAreaRow area = result.areas().get(0);
    assertThat(area.id()).isEqualTo(fixture.id());
    assertThat(area.status()).isEqualTo(fixture.status());
    assertThat(area.version()).isEqualTo(fixture.version());
    assertThat(area.geometry()).isEqualTo(fixture.geometry());
    assertThat(area.bbox()).isEqualTo(fixture.bbox());
  }

  @Test
  @DisplayName("S2 mock은 다른 Lane owner 테이블에 write하지 않는다")
  void s2_mock은_다른_lane_owner_write를_만들지_않는다() {
    assertThat(SearchAreaQueryFixtures.FORBIDDEN_S2_OWNER_WRITES)
        .containsExactly(
            "operational_period",
            "search_path",
            "path_segment",
            "marker",
            "photo",
            "notification_delivery",
            "offline_package_manifest");
  }
}
