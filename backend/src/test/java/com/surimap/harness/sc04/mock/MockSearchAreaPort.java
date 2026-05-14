package com.surimap.harness.sc04.mock;

import com.surimap.harness.sc04.fixture.Sc04Fixtures;
import com.surimap.maparea.fixture.GeometryFixtures;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.maparea.query.OverallSearchAreaResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SC-04 harness search_area(unit) mock port.
 *
 * <p>SearchAreaQuery.byOp / byIncident 를 스텁하여 SC-04 시나리오에서 unit 구역 존재를 제어한다.
 * overallOf 는 empty를 반환한다 (overall 전용 mock은 MockOverallSearchAreaPort 가 담당).
 *
 * <p>기준 문서: docs/spec/specs/S2.json §service_contracts, §harness_fixtures.sc04.
 */
public class MockSearchAreaPort implements SearchAreaQuery {

  private final List<String> publishedEventTypes = new ArrayList<>();
  private final List<SearchAreaRow> stubbedAreas = new ArrayList<>();

  /** SC-04 step-2: search_area(unit) 생성 후 호출. stub에 ACTIVE 구역을 등록한다. */
  public MockSearchAreaPort stubSearchAreaActive() {
    stubbedAreas.add(
        new SearchAreaRow(
            Sc04Fixtures.AREA_ID,
            Sc04Fixtures.INCIDENT_ID,
            Sc04Fixtures.OP1_ID,
            /* parentAreaId= */ null,
            "ACTIVE",
            /* areaLevel= */ null,
            Sc04Fixtures.AREA_CREATED_VERSION,
            GeometryFixtures.validSearchAreaPolygon(),
            GeometryFixtures.AREA_GEOMETRY_BBOX,
            Instant.parse("2026-04-28T00:30:00Z"),
            /* historyCount= */ 1L));
    publishedEventTypes.add("SEARCH_AREA_CHANGED");
    return this;
  }

  @Override
  public Optional<OverallSearchAreaResult> overallOf(UUID incidentId) {
    return Optional.empty();
  }

  @Override
  public SearchAreaCollection byIncident(UUID incidentId, SearchAreaFilters filters) {
    if (Sc04Fixtures.INCIDENT_ID.equals(incidentId)) {
      return new SearchAreaCollection(
          incidentId,
          stubbedAreas.stream().mapToLong(SearchAreaRow::version).max().orElse(0L),
          List.copyOf(stubbedAreas));
    }
    return new SearchAreaCollection(incidentId, 0L, List.of());
  }

  @Override
  public SearchAreaCollection byOp(UUID opId, SearchAreaFilters filters) {
    if (Sc04Fixtures.OP1_ID.equals(opId)) {
      return new SearchAreaCollection(
          Sc04Fixtures.INCIDENT_ID,
          stubbedAreas.stream().mapToLong(SearchAreaRow::version).max().orElse(0L),
          List.copyOf(stubbedAreas));
    }
    return new SearchAreaCollection(Sc04Fixtures.INCIDENT_ID, 0L, List.of());
  }

  public List<String> publishedEventTypes() {
    return List.copyOf(publishedEventTypes);
  }

  public List<SearchAreaRow> stubbedAreas() {
    return List.copyOf(stubbedAreas);
  }
}
