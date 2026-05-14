package com.surimap.board;

import com.surimap.maparea.query.SearchAreaAssignmentQuery;
import com.surimap.maparea.query.SearchAreaAssignmentRow;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.policephone.query.PolicePhoneFreshnessQuery;
import com.surimap.policephone.query.PolicePhoneFreshnessRow;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoardAreaPopupQueryService {

  private final ObjectProvider<SearchAreaQuery> searchAreaQuery;
  private final ObjectProvider<SearchAreaAssignmentQuery> searchAreaAssignmentQuery;
  private final ObjectProvider<PolicePhoneFreshnessQuery> policePhoneFreshnessQuery;
  private final OperationalPeriodQuery operationalPeriodQuery;

  public BoardAreaPopupQueryService(
      ObjectProvider<SearchAreaQuery> searchAreaQuery,
      ObjectProvider<SearchAreaAssignmentQuery> searchAreaAssignmentQuery,
      ObjectProvider<PolicePhoneFreshnessQuery> policePhoneFreshnessQuery,
      OperationalPeriodQuery operationalPeriodQuery) {
    this.searchAreaQuery = Objects.requireNonNull(searchAreaQuery, "searchAreaQuery must not be null");
    this.searchAreaAssignmentQuery =
        Objects.requireNonNull(searchAreaAssignmentQuery, "searchAreaAssignmentQuery must not be null");
    this.policePhoneFreshnessQuery =
        Objects.requireNonNull(policePhoneFreshnessQuery, "policePhoneFreshnessQuery must not be null");
    this.operationalPeriodQuery =
        Objects.requireNonNull(operationalPeriodQuery, "operationalPeriodQuery must not be null");
  }

  @Transactional(readOnly = true)
  public Optional<BoardAreaPopupResponse> getAreaPopup(UUID incidentId, UUID searchAreaId) {
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    Objects.requireNonNull(searchAreaId, "searchAreaId must not be null");
    SearchAreaQuery areaQuery = searchAreaQuery.getIfAvailable();
    if (areaQuery == null) {
      return Optional.empty();
    }

    SearchAreaRow area =
        areaQuery
            .byIncident(incidentId, new SearchAreaFilters(null, null, null, null, null, true))
            .areas()
            .stream()
            .filter(row -> searchAreaId.equals(row.id()))
            .findFirst()
            .orElse(null);
    if (area == null) {
      return Optional.empty();
    }

    OperationalPeriodRow op = operationalPeriod(area);
    List<SearchAreaAssignmentRow> assignments = assignments(searchAreaId);
    List<PolicePhoneFreshnessRow> freshnessRows = freshnessRows(incidentId);
    return Optional.of(BoardAreaPopupResponse.from(area, op, assignments, freshnessRows));
  }

  private OperationalPeriodRow operationalPeriod(SearchAreaRow area) {
    if (area.opId() == null) {
      return null;
    }
    return operationalPeriodQuery.list(area.incidentId()).stream()
        .filter(row -> area.opId().equals(row.opId()))
        .findFirst()
        .orElse(null);
  }

  private List<SearchAreaAssignmentRow> assignments(UUID searchAreaId) {
    SearchAreaAssignmentQuery query = searchAreaAssignmentQuery.getIfAvailable();
    if (query == null) {
      return List.of();
    }
    return query.byArea(searchAreaId);
  }

  private List<PolicePhoneFreshnessRow> freshnessRows(UUID incidentId) {
    PolicePhoneFreshnessQuery query = policePhoneFreshnessQuery.getIfAvailable();
    if (query == null) {
      return List.of();
    }
    return query.byIncident(incidentId);
  }
}
