package com.surimap.maparea;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.GeometryFixtures;
import com.surimap.maparea.fixture.SearchAreaQueryFixtures;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.testdouble.SearchAreaQueryMock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T04A SearchAreaQuery.overallOf mock contract 테스트.
 *
 * <p>real DB query 없이 소비 Lane이 canonical geometry, bbox, id/status/version 기준의 실패 contract test를
 * 실행할 수 있다.
 */
@DisplayName("L3-T04A SearchAreaQuery.overallOf mock contract")
class SearchAreaQueryOverallOfContractTest {

  private final SearchAreaQueryMock mock = new SearchAreaQueryMock();

  @Test
  @DisplayName("알려진 incidentId는 ACTIVE overall_search_area를 반환한다")
  void known_incidentId는_overall_search_area를_반환한다() {
    Optional<OverallSearchAreaResult> result = mock.overallOf(BoundaryAreaFixtures.INCIDENT_ID);

    assertThat(result).isPresent();
    OverallSearchAreaResult area = result.get();
    assertThat(area.id()).isEqualTo(BoundaryAreaFixtures.OVERALL_AREA_ID);
    assertThat(area.incidentId()).isEqualTo(BoundaryAreaFixtures.INCIDENT_ID);
    assertThat(area.status()).isEqualTo("ACTIVE");
    assertThat(area.version()).isEqualTo(BoundaryAreaFixtures.OVERALL_AREA_VERSION);
  }

  @Test
  @DisplayName("알 수 없는 incidentId는 empty를 반환한다")
  void 알_수_없는_incidentId는_empty를_반환한다() {
    Optional<OverallSearchAreaResult> result = mock.overallOf(UUID.randomUUID());

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("overallOf geometry는 canonical GeoJSON Polygon이다")
  void overallOf_geometry는_canonical_polygon이다() {
    OverallSearchAreaResult area = mock.overallOf(BoundaryAreaFixtures.INCIDENT_ID).orElseThrow();

    assertThat(area.geometry())
        .isEqualTo(SearchAreaQueryFixtures.overallSearchAreaResult().geometry());
    assertThat(area.geometry().type()).isEqualTo("Polygon");
    assertThat(area.geometry().coordinates())
        .containsExactly(GeometryFixtures.validOverallSearchAreaRing());
  }

  @Test
  @DisplayName("overallOf bbox는 [minLon, minLat, maxLon, maxLat] 순서를 따른다")
  void overallOf_bbox는_문서_순서와_일치한다() {
    OverallSearchAreaResult area = mock.overallOf(BoundaryAreaFixtures.INCIDENT_ID).orElseThrow();

    assertThat(area.bbox()).isEqualTo(GeometryFixtures.BOUNDARY_GEOMETRY_BBOX);
  }

  @Test
  @DisplayName("S3-2/S7/S3-1/S5 소비자는 같은 overallOf id/status/version/geometry를 비교한다")
  void 소비자는_같은_overall_fixture를_참조한다() {
    OverallSearchAreaResult area = mock.overallOf(BoundaryAreaFixtures.INCIDENT_ID).orElseThrow();
    OverallSearchAreaResult fixture = SearchAreaQueryFixtures.overallSearchAreaResult();

    assertThat(area.id()).isEqualTo(fixture.id());
    assertThat(area.status()).isEqualTo(fixture.status());
    assertThat(area.version()).isEqualTo(fixture.version());
    assertThat(area.geometry()).isEqualTo(fixture.geometry());
    assertThat(area.bbox()).isEqualTo(fixture.bbox());
  }
}
