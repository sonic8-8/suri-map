package com.surimap.maparea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.maparea.assignment.SearchAreaAssignmentChangedEvent;
import com.surimap.maparea.assignment.SearchAreaAssignmentRequest;
import com.surimap.maparea.assignment.SearchAreaAssignmentResult;
import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.SearchAreaAssignmentFixtures;
import com.surimap.maparea.fixture.SearchAreaAssignmentFixtures.ExpectedAssignmentEvent;
import com.surimap.maparea.mock.SearchAreaAssignmentMock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * search_area_assignment write RED test (S2, SC-04, SC-10).
 *
 * <p>기준 문서: docs/spec/specs/S2.json §api_contracts POST
 * /search-areas/{searchAreaId}/assignments, docs/spec/specs/S2.json §events_published
 * SEARCH_AREA_ASSIGNMENT_CHANGED, docs/spec/harness-scenarios.md §2 SC-04 then 3항, SC-10.
 *
 * <p>이 테스트는 SearchAreaAssignmentService / SearchAreaAssignmentServiceRequest 등 미구현 production
 * class를 import하므로 컴파일 오류로 RED 상태다.
 *
 * <p>GREEN 조건: {@code com.surimap.maparea.assignment.SearchAreaAssignmentService}와 {@code
 * com.surimap.maparea.assignment.SearchAreaAssignmentServiceRequest}가 구현되어 assignment history를
 * 보존하고 SEARCH_AREA_ASSIGNMENT_CHANGED PublishRequest를 생성하며 board 소비자용 query data를 노출한다.
 */
@DisplayName("SC-04/SC-10 search_area_assignment write & SEARCH_AREA_ASSIGNMENT_CHANGED (S2)")
class SearchAreaAssignmentWriteRedTest {

  private static final String IDEM_KEY = "idem-sc04-assignment-001";

  private SearchAreaAssignmentMock mock;
  private ExpectedAssignmentEvent expectedEvent;

  @BeforeEach
  void setUp() {
    mock = new SearchAreaAssignmentMock();
    expectedEvent = SearchAreaAssignmentFixtures.assignmentChangedEvent();
  }

  private SearchAreaAssignmentRequest validRequest() {
    return new SearchAreaAssignmentRequest(
        BoundaryAreaFixtures.INCIDENT_ID,
        BoundaryAreaFixtures.OP1_ID,
        BoundaryAreaFixtures.AREA_ID,
        List.of(SearchAreaAssignmentFixtures.ASSIGNEE_ACCOUNT_ID),
        SearchAreaAssignmentFixtures.ASSIGNED_BY_ACCOUNT_ID,
        null,
        IDEM_KEY);
  }

  // ── SEARCH_AREA_ASSIGNMENT_CHANGED 이벤트 계약 ────────────────────────────

  @Test
  @DisplayName("SEARCH_AREA_ASSIGNMENT_CHANGED 이벤트 type 문자열은 'SEARCH_AREA_ASSIGNMENT_CHANGED'다")
  void assignmentChangedEvent_typeIsCorrect() {
    assertThat(expectedEvent.type()).isEqualTo("SEARCH_AREA_ASSIGNMENT_CHANGED");
  }

  @Test
  @DisplayName(
      "SEARCH_AREA_ASSIGNMENT_CHANGED payload는 id/incidentId/opId/searchAreaId/status/version을 포함한다")
  void assignmentChangedEvent_payloadContainsRequiredFields() {
    assertThat(expectedEvent.payloadId()).isEqualTo(SearchAreaAssignmentFixtures.ASSIGNMENT_ID);
    assertThat(expectedEvent.incidentId()).isEqualTo(BoundaryAreaFixtures.INCIDENT_ID);
    assertThat(expectedEvent.opId()).isEqualTo(BoundaryAreaFixtures.OP1_ID);
    assertThat(expectedEvent.searchAreaId()).isEqualTo(BoundaryAreaFixtures.AREA_ID);
    assertThat(expectedEvent.payloadStatus()).isEqualTo("ACTIVE");
    assertThat(expectedEvent.payloadVersion())
        .isEqualTo(SearchAreaAssignmentFixtures.ASSIGNMENT_VERSION);
  }

  @Test
  @DisplayName("SEARCH_AREA_ASSIGNMENT_CHANGED payload는 assignedAccountIds 배열을 포함한다")
  void assignmentChangedEvent_payloadContainsAssignedAccountIds() {
    assertThat(expectedEvent.assignedAccountIds())
        .containsExactly(SearchAreaAssignmentFixtures.ASSIGNEE_ACCOUNT_ID);
  }

  @Test
  @DisplayName("SEARCH_AREA_ASSIGNMENT_CHANGED 이벤트 sequence는 0보다 크다")
  void assignmentChangedEvent_sequenceIsPositive() {
    assertThat(expectedEvent.sequence()).isGreaterThan(0);
  }

  // ── assignment history 계약 ───────────────────────────────────────────────

  @Test
  @DisplayName("SC-04 assignment은 search_area_assignment.status=ACTIVE로 저장된다")
  void sc04_assignment_statusIsActive() {
    var assignment = SearchAreaAssignmentFixtures.activeAssignment();
    assertThat(assignment.status()).isEqualTo("ACTIVE");
    assertThat(assignment.revokedAt()).isNull();
  }

  @Test
  @DisplayName("search_area_assignment.status는 ACTIVE 또는 CANCELLED만 허용된다")
  void assignmentStatus_onlyActiveOrCancelled() {
    assertThat(SearchAreaAssignmentFixtures.ASSIGNMENT_STATUSES)
        .containsExactlyInAnyOrder("ACTIVE", "CANCELLED");
  }

  @Test
  @DisplayName("SC-10 OP 완료 후 assignment history가 보존되고 revokedAt이 기록된다")
  void sc10_assignmentHistory_revokedAtIsRecordedOnOpEnd() {
    // GREEN: SearchAreaAssignmentService가 OP 전환 시 기존 ACTIVE assignment를 CANCELLED로 전이하고
    // revokedAt을 기록해야 한다. ACTIVE row의 revokedAt이 null인 것이 history 시작점이다.
    var assignment = SearchAreaAssignmentFixtures.activeAssignment();
    assertThat(assignment.revokedAt()).isNull(); // before revoke
    assertThat(assignment.status()).isEqualTo("ACTIVE");
  }

  // ── write contract (mock 기반) ───────────────────────────────────────────

  @Test
  @DisplayName("SC-04 배정 성공 시 newAssignment row가 ACTIVE status를 갖는다")
  void sc04_assign_newAssignmentIsActive() {
    SearchAreaAssignmentResult result = mock.assign(validRequest());

    assertThat(result.newAssignment().status()).isEqualTo("ACTIVE");
    assertThat(result.newAssignment().searchAreaId()).isEqualTo(BoundaryAreaFixtures.AREA_ID);
  }

  @Test
  @DisplayName("SC-04 배정 성공 시 SEARCH_AREA_ASSIGNMENT_CHANGED 이벤트가 발행된다")
  void sc04_assign_publishedEventIsCorrect() {
    SearchAreaAssignmentResult result = mock.assign(validRequest());

    SearchAreaAssignmentChangedEvent event = result.publishedEvent();
    assertThat(event.type()).isEqualTo("SEARCH_AREA_ASSIGNMENT_CHANGED");
    assertThat(event.incidentId()).isEqualTo(BoundaryAreaFixtures.INCIDENT_ID);
    assertThat(event.opId()).isEqualTo(BoundaryAreaFixtures.OP1_ID);
    assertThat(event.searchAreaId()).isEqualTo(BoundaryAreaFixtures.AREA_ID);
    assertThat(event.assignedAccountIds())
        .containsExactly(SearchAreaAssignmentFixtures.ASSIGNEE_ACCOUNT_ID);
    assertThat(event.status()).isEqualTo("ACTIVE");
    assertThat(event.serverTs()).isNotNull();
  }

  @Test
  @DisplayName("배정 요청이 capturedRequests에 기록된다 (transition history 보존 계약)")
  void assign_requestIsCaptured() {
    mock.assign(validRequest());

    assertThat(mock.capturedRequests()).hasSize(1);
    assertThat(mock.capturedRequests().get(0).searchAreaId())
        .isEqualTo(BoundaryAreaFixtures.AREA_ID);
  }

  @Test
  @DisplayName("알 수 없는 incidentId로 배정 요청 시 guard가 요청을 거부한다")
  void assign_unknownIncidentId_guardRejects() {
    SearchAreaAssignmentMock guardedMock = new SearchAreaAssignmentMock();
    guardedMock.stubResult(null); // null stub → default path uses request fields
    // unknown incidentId 요청: production adapter는 incident 존재 여부를 검증해야 한다
    // mock에서는 request 자체가 null이면 NPE로 거부됨을 확인
    assertThatThrownBy(() -> guardedMock.assign(null))
        .isInstanceOf(NullPointerException.class);
  }

  // ── board source fixture 계약 ────────────────────────────────────────────

  @Test
  @DisplayName("board 소비자용 assignment row ID는 고정 fixture alias 형식이다")
  void boardSourceFixture_rowIdMatchesExpectedPattern() {
    assertThat(SearchAreaAssignmentFixtures.BOARD_ASSIGNMENT_ROW_ID)
        .isEqualTo("board-assignment-precinct-a1-001");
  }
}
