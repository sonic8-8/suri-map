package com.surimap.maparea.testdouble;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.SearchAreaQueryFixtures;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaQuery;
import java.util.Optional;
import java.util.UUID;

/**
 * L3-T04A SearchAreaQuery mock test double.
 *
 * <p>real DB 없이 소비 Lane이 canonical geometry, bbox, id/status/version 기준의 contract test를 실행할 수 있게
 * 한다.
 */
public final class SearchAreaQueryMock implements SearchAreaQuery {

  @Override
  public Optional<OverallSearchAreaResult> overallOf(UUID incidentId) {
    if (BoundaryAreaFixtures.INCIDENT_ID.equals(incidentId)) {
      return Optional.of(SearchAreaQueryFixtures.overallSearchAreaResult());
    }
    return Optional.empty();
  }

  @Override
  public SearchAreaCollection byIncident(UUID incidentId, SearchAreaFilters filters) {
    if (BoundaryAreaFixtures.INCIDENT_ID.equals(incidentId)) {
      return SearchAreaQueryFixtures.byIncidentResult();
    }
    return emptyCollection(incidentId);
  }

  @Override
  public SearchAreaCollection byOp(UUID opId, SearchAreaFilters filters) {
    if (BoundaryAreaFixtures.OP1_ID.equals(opId)) {
      return SearchAreaQueryFixtures.byOp1Result();
    }
    return emptyCollection(BoundaryAreaFixtures.INCIDENT_ID);
  }

  private static SearchAreaCollection emptyCollection(UUID incidentId) {
    return new SearchAreaCollection(incidentId, 0L, java.util.List.of());
  }
}
