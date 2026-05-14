package com.surimap.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaAssignmentQuery;
import com.surimap.maparea.query.SearchAreaAssignmentRow;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.policephone.PolicePhoneFreshnessStatus;
import com.surimap.policephone.query.PolicePhoneFreshnessQuery;
import com.surimap.policephone.query.PolicePhoneFreshnessRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

@DisplayName("board area popup query service")
class BoardAreaPopupQueryServiceTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID AREA_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");
  private static final UUID OP_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
  private static final UUID ASSIGNMENT_ID = UUID.fromString("31000000-0000-4000-8000-000000000001");
  private static final UUID ASSIGNED_ACCOUNT_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110003");
  private static final UUID ASSIGNED_BY_ACCOUNT_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110004");
  private static final UUID POLICE_PHONE_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
  private static final Instant STARTED_AT = Instant.parse("2026-04-28T00:00:00Z");
  private static final Instant ASSIGNED_AT = Instant.parse("2026-04-28T00:10:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-04-28T00:20:00Z");
  private static final Instant COMPLETED_AT = Instant.parse("2026-04-28T00:30:00Z");

  @Test
  @DisplayName("ACTIVE area popup serves common and incomplete summary without completion fields")
  void activeAreaPopupServesIncompleteSummary() {
    CapturingSearchAreaQuery searchAreaQuery = new CapturingSearchAreaQuery(activeArea());
    BoardAreaPopupQueryService service =
        new BoardAreaPopupQueryService(
            provider(searchAreaQuery),
            provider(new FakeAssignmentQuery()),
            provider(new FakeFreshnessQuery()),
            new FakeOperationalPeriodQuery());

    BoardAreaPopupResponse response =
        service.getAreaPopup(INCIDENT_ID, AREA_ID).orElseThrow();

    assertThat(searchAreaQuery.filters().includeCancelled()).isTrue();
    assertThat(response.common().areaName()).isEqualTo("A구역");
    assertThat(response.common().areaLevel()).isEqualTo("TEAM");
    assertThat(response.common().areaStatus()).isEqualTo("ACTIVE");
    assertThat(response.common().opSequence()).isEqualTo(1);
    assertThat(response.common().assignmentStatus()).isEqualTo("ASSIGNED");
    assertThat(response.common().assignments()).singleElement()
        .satisfies(
            assignment -> {
              assertThat(assignment.assignedAccountId()).isEqualTo(ASSIGNED_ACCOUNT_ID);
              assertThat(assignment.accountType()).isEqualTo(AccountType.TEAM);
              assertThat(assignment.organizationType()).isEqualTo(OrganizationType.POLICE_SUBSTATION);
              assertThat(assignment.policePhone().policePhoneId()).isEqualTo(POLICE_PHONE_ID);
              assertThat(assignment.policePhone().freshness()).isEqualTo("ONLINE");
            });
    assertThat(response.incompleteSummary()).isNotNull();
    assertThat(response.completedSummary()).isNull();
  }

  @Test
  @DisplayName("COMPLETED area popup serves completed summary and omits incomplete summary")
  void completedAreaPopupServesCompletedSummary() {
    BoardAreaPopupQueryService service =
        new BoardAreaPopupQueryService(
            provider(new CapturingSearchAreaQuery(completedArea())),
            provider(new FakeAssignmentQuery()),
            provider(new FakeFreshnessQuery()),
            new FakeOperationalPeriodQuery());

    BoardAreaPopupResponse response =
        service.getAreaPopup(INCIDENT_ID, AREA_ID).orElseThrow();

    assertThat(response.common().areaStatus()).isEqualTo("COMPLETED");
    assertThat(response.incompleteSummary()).isNull();
    assertThat(response.completedSummary()).isNotNull();
    assertThat(response.completedSummary().completedAt()).isEqualTo(COMPLETED_AT);
    assertThat(response.completedSummary().completedByAccountId()).isEqualTo(ASSIGNED_BY_ACCOUNT_ID);
    assertThat(response.completedSummary().completionMemo()).isEqualTo("무전 확인 후 완료");
  }

  private static SearchAreaRow activeArea() {
    return area("ACTIVE", null, null, null);
  }

  private static SearchAreaRow completedArea() {
    return area("COMPLETED", COMPLETED_AT, ASSIGNED_BY_ACCOUNT_ID, "무전 확인 후 완료");
  }

  private static SearchAreaRow area(
      String status, Instant completedAt, UUID completedByAccountId, String completionMemo) {
    return new SearchAreaRow(
        AREA_ID,
        INCIDENT_ID,
        OP_ID,
        null,
        "A구역",
        "TEAM",
        status,
        5L,
        polygon(),
        List.of(
            new BigDecimal("126.950000"),
            new BigDecimal("37.570000"),
            new BigDecimal("126.952000"),
            new BigDecimal("37.572000")),
        UPDATED_AT,
        2L,
        completedAt,
        completedByAccountId,
        completionMemo);
  }

  private static GeoJsonPolygon polygon() {
    return new GeoJsonPolygon(
        "Polygon",
        List.of(
            List.of(
                List.of(new BigDecimal("126.950000"), new BigDecimal("37.570000")),
                List.of(new BigDecimal("126.952000"), new BigDecimal("37.570000")),
                List.of(new BigDecimal("126.952000"), new BigDecimal("37.572000")),
                List.of(new BigDecimal("126.950000"), new BigDecimal("37.572000")),
                List.of(new BigDecimal("126.950000"), new BigDecimal("37.570000")))));
  }

  private static <T> ObjectProvider<T> provider(T value) {
    return new ObjectProvider<>() {
      @Override
      public T getObject() {
        return value;
      }

      @Override
      public T getIfAvailable() {
        return value;
      }

      @Override
      public Stream<T> stream() {
        return value == null ? Stream.empty() : Stream.of(value);
      }
    };
  }

  private static final class CapturingSearchAreaQuery implements SearchAreaQuery {
    private final SearchAreaRow area;
    private SearchAreaFilters filters;

    private CapturingSearchAreaQuery(SearchAreaRow area) {
      this.area = area;
    }

    @Override
    public Optional<OverallSearchAreaResult> overallOf(UUID incidentId) {
      return Optional.empty();
    }

    @Override
    public SearchAreaCollection byIncident(UUID incidentId, SearchAreaFilters filters) {
      this.filters = filters;
      return new SearchAreaCollection(incidentId, area.version(), List.of(area));
    }

    @Override
    public SearchAreaCollection byOp(UUID opId, SearchAreaFilters filters) {
      return new SearchAreaCollection(INCIDENT_ID, area.version(), List.of(area));
    }

    private SearchAreaFilters filters() {
      return filters;
    }
  }

  private static final class FakeAssignmentQuery implements SearchAreaAssignmentQuery {
    @Override
    public List<SearchAreaAssignmentRow> byOp(UUID opId) {
      return List.of(assignment());
    }

    @Override
    public List<SearchAreaAssignmentRow> byArea(UUID searchAreaId) {
      return AREA_ID.equals(searchAreaId) ? List.of(assignment()) : List.of();
    }

    private static SearchAreaAssignmentRow assignment() {
      return new SearchAreaAssignmentRow(
          ASSIGNMENT_ID,
          AREA_ID,
          ASSIGNED_ACCOUNT_ID,
          ASSIGNED_BY_ACCOUNT_ID,
          ASSIGNED_AT,
          null,
          "ACTIVE",
          5L);
    }
  }

  private static final class FakeFreshnessQuery implements PolicePhoneFreshnessQuery {
    @Override
    public List<PolicePhoneFreshnessRow> byIncident(UUID incidentId) {
      return List.of(
          new PolicePhoneFreshnessRow(
              POLICE_PHONE_ID,
              ASSIGNED_ACCOUNT_ID.toString(),
              AccountType.TEAM,
              OrganizationType.POLICE_SUBSTATION,
              incidentId,
              OP_ID,
              Instant.parse("2026-04-28T00:12:00Z"),
              Instant.parse("2026-04-28T00:11:00Z"),
              4L,
              PolicePhoneFreshnessStatus.ONLINE));
    }
  }

  private static final class FakeOperationalPeriodQuery implements OperationalPeriodQuery {
    @Override
    public Optional<CurrentOpResult> current(UUID incidentId) {
      return Optional.of(new CurrentOpResult(OP_ID, incidentId, "ACTIVE", 1, STARTED_AT, null, null, 4L));
    }

    @Override
    public List<OperationalPeriodRow> list(UUID incidentId) {
      return List.of(new OperationalPeriodRow(OP_ID, incidentId, "ACTIVE", 1, STARTED_AT, null, null, 4L));
    }
  }
}
