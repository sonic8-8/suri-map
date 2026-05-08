package com.surimap.harness.sc04.mock;

import com.surimap.harness.sc04.fixture.Sc04Fixtures;
import com.surimap.maparea.fixture.GeometryFixtures;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SC-04 harness overall_search_area mock port.
 *
 * <p>SearchAreaQuery.overallOf をスタブし, SC-04 시나리오에서 ACTIVE overall_search_area 존재 여부를
 * 제어한다. byIncident / byOp 는 빈 컬렉션을 반환한다 (SC-04 harness에서 미사용).
 *
 * <p>기준 문서: docs/spec/specs/S2.json §service_contracts, §harness_fixtures.sc04.
 */
public class MockOverallSearchAreaPort implements SearchAreaQuery {

  private final List<String> publishedEventTypes = new ArrayList<>();
  private OverallSearchAreaResult stubbedOverall;

  /** SC-04 step-1: overall_search_area 생성 후 호출. stub에 ACTIVE overall을 등록한다. */
  public MockOverallSearchAreaPort stubOverallActive() {
    this.stubbedOverall =
        new OverallSearchAreaResult(
            Sc04Fixtures.OVERALL_AREA_ID,
            Sc04Fixtures.INCIDENT_ID,
            "ACTIVE",
            Sc04Fixtures.OVERALL_AREA_VERSION,
            GeometryFixtures.validOverallSearchAreaPolygon(),
            GeometryFixtures.BOUNDARY_GEOMETRY_BBOX,
            Instant.parse("2026-04-28T00:00:00Z"));
    publishedEventTypes.add("SEARCH_AREA_CHANGED");
    return this;
  }

  @Override
  public Optional<OverallSearchAreaResult> overallOf(UUID incidentId) {
    if (Sc04Fixtures.INCIDENT_ID.equals(incidentId) && stubbedOverall != null) {
      return Optional.of(stubbedOverall);
    }
    return Optional.empty();
  }

  @Override
  public SearchAreaCollection byIncident(UUID incidentId, SearchAreaFilters filters) {
    return new SearchAreaCollection(incidentId, 0L, List.of());
  }

  @Override
  public SearchAreaCollection byOp(UUID opId, SearchAreaFilters filters) {
    return new SearchAreaCollection(opId, 0L, List.of());
  }

  public List<String> publishedEventTypes() {
    return List.copyOf(publishedEventTypes);
  }
}
