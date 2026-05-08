package com.surimap.harness.sc04;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.harness.sc04.fixture.Sc04Fixtures;
import com.surimap.harness.sc04.mock.MockOverallSearchAreaPort;
import com.surimap.harness.sc04.mock.MockSearchAreaAssignmentPort;
import com.surimap.harness.sc04.mock.MockSearchAreaPort;
import com.surimap.maparea.assignment.SearchAreaAssignmentChangedEvent;
import com.surimap.maparea.assignment.SearchAreaAssignmentRequest;
import com.surimap.maparea.assignment.SearchAreaAssignmentResult;
import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.SearchAreaAssignmentFixtures;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaAssignmentRow;
import com.surimap.maparea.query.SearchAreaCollection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * SC-04 domain harness: 전체 수색 구역 설정 → 구역 생성 → 담당 구역 배정 순서 검증.
 *
 * <p>기준 문서: docs/spec/specs/S2.json §harness_constraints (SC-04),
 * docs/spec/harness-scenarios.md §2 SC-04.
 * (S8.json §harness_fixtures에는 sc04 섹션이 없으므로 S2.json harness_constraints를 단독 기준으로 사용)
 *
 * <p>L2/S4/S3-2 mock 으로만 동작한다. 실제 DB/Spring 없음.
 * 이벤트 발행(write-side) 계약 검증은 L3-T06A/T06B (Phase 2 mock contract) 에서 담당한다.
 * 이 harness는 query-side 수렴 증거와 assignment command 계약을 검증한다.
 */
@DisplayName("SC-04 수색 구역 harness")
class Sc04SearchAreaHarnessTest {

  private MockOverallSearchAreaPort overallPort;
  private MockSearchAreaPort searchAreaPort;
  private MockSearchAreaAssignmentPort assignmentPort;

  @BeforeEach
  void setUp() {
    overallPort = new MockOverallSearchAreaPort();
    searchAreaPort = new MockSearchAreaPort();
    assignmentPort = new MockSearchAreaAssignmentPort();
  }

  // ── Step 1: overall_search_area ─────────────────────────────────────────

  @Nested
  @DisplayName("SC-04 Step-1: overall_search_area 설정")
  class OverallSearchAreaStep {

    @Test
    @DisplayName("overall_search_area 생성 후 ACTIVE 상태로 조회된다")
    void overallSearchAreaIsActiveAfterCreation() {
      overallPort.stubOverallActive();

      var result = overallPort.overallOf(Sc04Fixtures.INCIDENT_ID);

      assertThat(result).isPresent();
      OverallSearchAreaResult overall = result.get();
      assertThat(overall.id()).isEqualTo(Sc04Fixtures.OVERALL_AREA_ID);
      assertThat(overall.incidentId()).isEqualTo(Sc04Fixtures.INCIDENT_ID);
      assertThat(overall.status()).isEqualTo("ACTIVE");
      assertThat(overall.version()).isEqualTo(Sc04Fixtures.OVERALL_AREA_VERSION);
    }

    @Test
    @DisplayName("overall_search_area fixture alias/version이 S2.json harness_constraints spec 리터럴과 일치한다")
    void overallFixtureAliasMatchesSpecLiteral() {
      // S2.json harness_constraints: overallAreaAlias=osa-precinct-001, version=2
      assertThat(BoundaryAreaFixtures.OVERALL_AREA_ALIAS).isEqualTo("osa-precinct-001-v1");
      assertThat(BoundaryAreaFixtures.OVERALL_AREA_VERSION).isEqualTo(2L);
      assertThat(BoundaryAreaFixtures.OVERALL_AREA_EVENT_ID).isEqualTo("evt-s2-overall-area-001");
      assertThat(BoundaryAreaFixtures.OVERALL_AREA_EVENT_SEQUENCE).isEqualTo(401L);
    }

    @Test
    @DisplayName("다른 incidentId로 조회하면 empty를 반환한다")
    void unknownIncidentReturnsEmpty() {
      overallPort.stubOverallActive();

      var result = overallPort.overallOf(BoundaryAreaFixtures.OP2_ID);

      assertThat(result).isEmpty();
    }
  }

  // ── Step 2: search_area(unit) ────────────────────────────────────────────

  @Nested
  @DisplayName("SC-04 Step-2: search_area(unit) 생성")
  class SearchAreaStep {

    @Test
    @DisplayName("search_area 생성 후 byOp로 ACTIVE 구역이 조회된다")
    void searchAreaIsActiveAfterCreation() {
      searchAreaPort.stubSearchAreaActive();

      SearchAreaCollection collection = searchAreaPort.byOp(Sc04Fixtures.OP1_ID, null);

      assertThat(collection.areas()).hasSize(1);
      var area = collection.areas().get(0);
      assertThat(area.id()).isEqualTo(Sc04Fixtures.AREA_ID);
      assertThat(area.status()).isEqualTo("ACTIVE");
      assertThat(area.version()).isEqualTo(Sc04Fixtures.AREA_CREATED_VERSION);
      assertThat(area.opId()).isEqualTo(Sc04Fixtures.OP1_ID);
      assertThat(area.incidentId()).isEqualTo(Sc04Fixtures.INCIDENT_ID);
    }

    @Test
    @DisplayName("search_area fixture alias/version이 S2.json harness_constraints spec 리터럴과 일치한다")
    void searchAreaFixtureAliasMatchesSpecLiteral() {
      // S2.json harness_constraints: areaAlias=area-precinct-a1, version=1
      assertThat(BoundaryAreaFixtures.AREA_ALIAS).isEqualTo("area-precinct-a1");
      assertThat(BoundaryAreaFixtures.AREA_CREATED_VERSION).isEqualTo(1L);
      assertThat(BoundaryAreaFixtures.AREA_CREATED_EVENT_ID).isEqualTo("evt-s2-area-created-001");
      assertThat(BoundaryAreaFixtures.AREA_CREATED_EVENT_SEQUENCE).isEqualTo(402L);
    }

    @Test
    @DisplayName("byIncident로도 동일한 구역이 조회된다")
    void searchAreaIsQueryableByIncident() {
      searchAreaPort.stubSearchAreaActive();

      SearchAreaCollection collection = searchAreaPort.byIncident(Sc04Fixtures.INCIDENT_ID, null);

      assertThat(collection.areas()).hasSize(1);
      assertThat(collection.areas().get(0).id()).isEqualTo(Sc04Fixtures.AREA_ID);
    }
  }

  // ── Step 3: search_area_assignment ──────────────────────────────────────

  @Nested
  @DisplayName("SC-04 Step-3: 담당 구역 배정")
  class AssignmentStep {

    private SearchAreaAssignmentRequest validRequest() {
      return new SearchAreaAssignmentRequest(
          Sc04Fixtures.INCIDENT_ID,
          Sc04Fixtures.OP1_ID,
          Sc04Fixtures.AREA_ID,
          List.of(Sc04Fixtures.ASSIGNEE_ACCOUNT_ID),
          Sc04Fixtures.ASSIGNED_BY_ACCOUNT_ID,
          null,
          "idem-sc04-assign-001");
    }

    @Test
    @DisplayName("구역 배정 후 SEARCH_AREA_ASSIGNMENT_CHANGED 이벤트가 발행된다")
    void assignmentPublishesChangedEvent() {
      SearchAreaAssignmentResult result = assignmentPort.assign(validRequest());

      assertThat(result.publishedEvent()).isNotNull();
      assertThat(result.publishedEvent().type())
          .isEqualTo("SEARCH_AREA_ASSIGNMENT_CHANGED");
    }

    @Test
    @DisplayName("발행된 이벤트 payload의 incidentId/opId/searchAreaId/status/version이 fixture와 일치한다")
    void assignmentEventPayloadMatchesFixture() {
      SearchAreaAssignmentResult result = assignmentPort.assign(validRequest());
      SearchAreaAssignmentChangedEvent event = result.publishedEvent();

      assertThat(event.incidentId()).isEqualTo(Sc04Fixtures.INCIDENT_ID);
      assertThat(event.opId()).isEqualTo(Sc04Fixtures.OP1_ID);
      assertThat(event.searchAreaId()).isEqualTo(Sc04Fixtures.AREA_ID);
      assertThat(event.status()).isEqualTo("ACTIVE");
      assertThat(event.version()).isEqualTo(Sc04Fixtures.ASSIGNMENT_VERSION);
      assertThat(event.assignedAccountIds()).containsExactly(Sc04Fixtures.ASSIGNEE_ACCOUNT_ID);
    }

    @Test
    @DisplayName("발행된 이벤트 eventId/sequence가 S2.json harness_constraints fixture 리터럴과 일치한다")
    void assignmentEventIdAndSequenceMatchFixture() {
      SearchAreaAssignmentResult result = assignmentPort.assign(validRequest());
      SearchAreaAssignmentChangedEvent event = result.publishedEvent();

      // S2.json harness_constraints: assignmentChangedEventId=evt-s2-assignment-001, sequence=410
      assertThat(event.eventId()).isEqualTo("evt-s2-assignment-001");
      assertThat(event.sequence()).isEqualTo(410L);
    }

    @Test
    @DisplayName("assignment fixture alias/version이 S2.json harness_constraints spec 리터럴과 일치한다")
    void assignmentFixtureAliasMatchesSpecLiteral() {
      // S2.json harness_constraints: assignmentAlias=saa-precinct-a1-001, version=1
      assertThat(SearchAreaAssignmentFixtures.ASSIGNMENT_ALIAS).isEqualTo("saa-precinct-a1-001");
      assertThat(SearchAreaAssignmentFixtures.ASSIGNMENT_VERSION).isEqualTo(1L);
      assertThat(SearchAreaAssignmentFixtures.ASSIGNMENT_CHANGED_EVENT_ID)
          .isEqualTo("evt-s2-assignment-001");
      assertThat(SearchAreaAssignmentFixtures.ASSIGNMENT_CHANGED_EVENT_SEQUENCE).isEqualTo(410L);
    }

    @Test
    @DisplayName("배정 후 byOp로 ACTIVE assignment가 조회된다 — board convergence")
    void assignmentIsQueryableByOpAfterAssign() {
      assignmentPort.assign(validRequest());

      List<SearchAreaAssignmentRow> rows = assignmentPort.byOp(Sc04Fixtures.OP1_ID);

      assertThat(rows).hasSize(1);
      SearchAreaAssignmentRow row = rows.get(0);
      assertThat(row.searchAreaId()).isEqualTo(Sc04Fixtures.AREA_ID);
      assertThat(row.status()).isEqualTo("ACTIVE");
      assertThat(row.version()).isEqualTo(Sc04Fixtures.ASSIGNMENT_VERSION);
    }

    @Test
    @DisplayName("배정 후 byArea로 ACTIVE assignment가 조회된다")
    void assignmentIsQueryableByArea() {
      assignmentPort.assign(validRequest());

      List<SearchAreaAssignmentRow> rows = assignmentPort.byArea(Sc04Fixtures.AREA_ID);

      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).assignedAccountId())
          .isEqualTo(Sc04Fixtures.ASSIGNEE_ACCOUNT_ID);
    }
  }

  // ── Step 4: board convergence evidence ──────────────────────────────────

  @Nested
  @DisplayName("SC-04 Step-4: board convergence evidence")
  class BoardConvergenceStep {

    @Test
    @DisplayName("board row ID 상수가 S2.json harness_constraints spec 리터럴과 일치한다")
    void boardRowIdsMatchSpecLiterals() {
      // S2.json harness_constraints board row IDs
      assertThat(BoundaryAreaFixtures.BOARD_OVERALL_SEARCH_AREA_ROW_ID)
          .isEqualTo("board-overall-search-area-inc-precinct-first-001");
      assertThat(BoundaryAreaFixtures.BOARD_AREA_ROW_ID)
          .isEqualTo("board-area-precinct-a1");
      assertThat(SearchAreaAssignmentFixtures.BOARD_ASSIGNMENT_ROW_ID)
          .isEqualTo("board-assignment-precinct-a1-001");
    }

    @Test
    @DisplayName("배정 후 byOp board row의 version이 fixture version 이상이다 — board 수렴 증거")
    void boardAssignmentRowVersionIsAtLeastFixtureVersion() {
      var req = new SearchAreaAssignmentRequest(
          Sc04Fixtures.INCIDENT_ID,
          Sc04Fixtures.OP1_ID,
          Sc04Fixtures.AREA_ID,
          List.of(Sc04Fixtures.ASSIGNEE_ACCOUNT_ID),
          Sc04Fixtures.ASSIGNED_BY_ACCOUNT_ID,
          null,
          "idem-sc04-board-001");
      assignmentPort.assign(req);

      List<SearchAreaAssignmentRow> boardRows = assignmentPort.byOp(Sc04Fixtures.OP1_ID);

      assertThat(boardRows).hasSize(1);
      // S2.json harness_constraints: "expected board response version >= REST response version"
      assertThat(boardRows.get(0).version())
          .isGreaterThanOrEqualTo(SearchAreaAssignmentFixtures.ASSIGNMENT_VERSION);
      assertThat(boardRows.get(0).searchAreaId()).isEqualTo(Sc04Fixtures.AREA_ID);
    }

    @Test
    @DisplayName("overall + area + assignment 전체 SC-04 시나리오 순서가 mock으로 통과한다")
    void fullSc04ScenarioPasses() {
      // Step-1: overall
      overallPort.stubOverallActive();
      assertThat(overallPort.overallOf(Sc04Fixtures.INCIDENT_ID)).isPresent();

      // Step-2: search_area
      searchAreaPort.stubSearchAreaActive();
      assertThat(searchAreaPort.byOp(Sc04Fixtures.OP1_ID, null).areas()).hasSize(1);

      // Step-3: assignment (command returns event)
      var req = new SearchAreaAssignmentRequest(
          Sc04Fixtures.INCIDENT_ID,
          Sc04Fixtures.OP1_ID,
          Sc04Fixtures.AREA_ID,
          List.of(Sc04Fixtures.ASSIGNEE_ACCOUNT_ID),
          Sc04Fixtures.ASSIGNED_BY_ACCOUNT_ID,
          null,
          "idem-sc04-full-001");
      SearchAreaAssignmentResult assignResult = assignmentPort.assign(req);
      assertThat(assignResult.publishedEvent().type())
          .isEqualTo("SEARCH_AREA_ASSIGNMENT_CHANGED");
      assertThat(assignResult.publishedEvent().eventId())
          .isEqualTo("evt-s2-assignment-001");

      // Step-4: board convergence query
      List<SearchAreaAssignmentRow> boardRows = assignmentPort.byOp(Sc04Fixtures.OP1_ID);
      assertThat(boardRows).hasSize(1);
      assertThat(boardRows.get(0).searchAreaId()).isEqualTo(Sc04Fixtures.AREA_ID);
      assertThat(boardRows.get(0).status()).isEqualTo("ACTIVE");
    }
  }
}
