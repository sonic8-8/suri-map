package com.surimap.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.domain.summary.ForbiddenSummaryGuard;
import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.SearchAreaAssignmentFixtures;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Demo fixture 유효성 검증 테스트.
 *
 * <p>기준 문서: prd.md §5.1, docs/spec/harness-scenarios.md §6 mock 112 배정 사건.
 *
 * <p>live manual data repair 없이 demo fixture만으로 시연이 가능한지 확인한다.
 */
@DisplayName("시연용 Demo Fixture 유효성 검증")
class DemoScenarioFixturesTest {

  // ── 공통 ID 일관성 ────────────────────────────────────────────────────────

  @Nested
  @DisplayName("공통 incident/OP ID 일관성")
  class IncidentOpConsistency {

    @Test
    @DisplayName("INCIDENT_ID가 모든 하위 fixture와 동일하다")
    void incidentIdIsConsistentAcrossFixtures() {
      assertThat(DemoScenarioFixtures.INCIDENT_ID)
          .isEqualTo(BoundaryAreaFixtures.INCIDENT_ID)
          .isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    }

    @Test
    @DisplayName("OP1_ID가 SC-04/SC-10 fixture와 동일하다")
    void op1IdIsConsistentAcrossFixtures() {
      assertThat(DemoScenarioFixtures.OP1_ID)
          .isEqualTo(BoundaryAreaFixtures.OP1_ID)
          .isEqualTo(OperationalPeriodFixtures.OP1_ID);
    }

    @Test
    @DisplayName("OP2_ID가 SC-10 fixture와 동일하다")
    void op2IdIsConsistentAcrossFixtures() {
      assertThat(DemoScenarioFixtures.OP2_ID)
          .isEqualTo(BoundaryAreaFixtures.OP2_ID)
          .isEqualTo(OperationalPeriodFixtures.OP2_ID);
    }
  }

  // ── SC-04 fixture 검증 ───────────────────────────────────────────────────

  @Nested
  @DisplayName("SC-04 수색 구역·배정 fixture 유효성")
  class Sc04FixtureValidity {

    @Test
    @DisplayName("overall_search_area alias/version이 spec 리터럴과 일치한다")
    void overallAreaFixtureMatchesSpec() {
      assertThat(DemoScenarioFixtures.OVERALL_AREA_ALIAS).isEqualTo("osa-precinct-001-v1");
      assertThat(DemoScenarioFixtures.OVERALL_AREA_VERSION).isEqualTo(2L);
      assertThat(DemoScenarioFixtures.OVERALL_AREA_EVENT_ID).isEqualTo("evt-s2-overall-area-001");
      assertThat(DemoScenarioFixtures.OVERALL_AREA_STATUS).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("search_area alias/version이 spec 리터럴과 일치한다")
    void searchAreaFixtureMatchesSpec() {
      assertThat(DemoScenarioFixtures.AREA_ALIAS).isEqualTo("area-precinct-a1");
      assertThat(DemoScenarioFixtures.AREA_VERSION_CREATED).isEqualTo(1L);
      assertThat(DemoScenarioFixtures.AREA_CREATED_EVENT_ID).isEqualTo("evt-s2-area-created-001");
      assertThat(DemoScenarioFixtures.AREA_STATUS_ACTIVE).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("assignment alias/version/eventId가 spec 리터럴과 일치한다")
    void assignmentFixtureMatchesSpec() {
      assertThat(DemoScenarioFixtures.ASSIGNMENT_ALIAS).isEqualTo("saa-precinct-a1-001");
      assertThat(DemoScenarioFixtures.ASSIGNMENT_VERSION).isEqualTo(1L);
      assertThat(DemoScenarioFixtures.ASSIGNMENT_EVENT_ID).isEqualTo("evt-s2-assignment-001");
      assertThat(DemoScenarioFixtures.ASSIGNMENT_EVENT_SEQUENCE).isEqualTo(410L);
      assertThat(DemoScenarioFixtures.ASSIGNMENT_STATUS).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("board row ID가 spec 리터럴과 일치한다")
    void boardRowIdsMatchSpec() {
      assertThat(DemoScenarioFixtures.BOARD_OVERALL_SEARCH_AREA_ROW_ID)
          .isEqualTo("board-overall-search-area-inc-precinct-first-001");
      assertThat(DemoScenarioFixtures.BOARD_AREA_ROW_ID)
          .isEqualTo("board-area-precinct-a1");
      assertThat(DemoScenarioFixtures.BOARD_ASSIGNMENT_ROW_ID)
          .isEqualTo("board-assignment-precinct-a1-001");
    }

    @Test
    @DisplayName("overall_search_area bbox가 null이 아니고 4개 좌표를 가진다")
    void overallAreaBboxIsValid() {
      assertThat(DemoScenarioFixtures.OVERALL_AREA_BBOX).hasSize(4);
      assertThat(DemoScenarioFixtures.AREA_BBOX).hasSize(4);
    }
  }

  // ── SC-10 fixture 검증 ───────────────────────────────────────────────────

  @Nested
  @DisplayName("SC-10 OP 전환·인수인계 fixture 유효성")
  class Sc10FixtureValidity {

    @Test
    @DisplayName("OP1 전환 후 상태 fixture가 spec과 일치한다")
    void op1AfterTransitionMatchesSpec() {
      assertThat(DemoScenarioFixtures.OP1_ALIAS).isEqualTo("op-precinct-001-op1");
      assertThat(DemoScenarioFixtures.OP1_STATUS_ENDED).isEqualTo("ENDED");
      assertThat(DemoScenarioFixtures.OP1_VERSION_ENDED).isEqualTo(2L);
      assertThat(DemoScenarioFixtures.OP1_SEQUENCE_NO).isEqualTo(1);
    }

    @Test
    @DisplayName("OP2 fixture가 spec 리터럴과 일치한다")
    void op2FixtureMatchesSpec() {
      assertThat(DemoScenarioFixtures.OP2_ALIAS).isEqualTo("op-precinct-001-op2");
      assertThat(DemoScenarioFixtures.OP2_STATUS).isEqualTo("ACTIVE");
      assertThat(DemoScenarioFixtures.OP2_REASON).isEqualTo("RE_SEARCH");
      assertThat(DemoScenarioFixtures.OP2_SEQUENCE_NO).isEqualTo(2);
      assertThat(DemoScenarioFixtures.OP2_VERSION).isEqualTo(1L);
    }

    @Test
    @DisplayName("SC-10 seed memo와 SC-11 OP2 memo fixture가 spec 리터럴과 일치한다")
    void handoverMemoFixtureMatchesSpec() {
      assertThat(DemoScenarioFixtures.SC10_HANDOVER_MEMO_ALIAS).isEqualTo("memo-precinct-handover-001");
      assertThat(DemoScenarioFixtures.SC10_HANDOVER_MEMO_ID)
          .hasToString("eeeeeeee-eeee-eeee-eeee-eeeeeeee0001");
      assertThat(DemoScenarioFixtures.SC10_HANDOVER_MEMO_BOARD_EVENT_ID)
          .isEqualTo("evt-s8-handover-created-001");
      assertThat(DemoScenarioFixtures.HANDOVER_MEMO_ALIAS).isEqualTo("memo-precinct-op2-001");
      assertThat(DemoScenarioFixtures.HANDOVER_MEMO_EVENT_ID).isEqualTo("evt-s8-handover-memo-001");
      assertThat(DemoScenarioFixtures.HANDOVER_MEMO_VERSION).isEqualTo(1L);
      assertThat(DemoScenarioFixtures.HANDOVER_MEMO_STATUS).isEqualTo("ACTIVE");
      assertThat(DemoScenarioFixtures.HANDOVER_MEMO_TARGET_TYPE).isEqualTo("OPERATIONAL_PERIOD");
    }

    @Test
    @DisplayName("handover memo content가 null이 아니고 비어 있지 않다")
    void handoverMemoContentIsValid() {
      assertThat(DemoScenarioFixtures.HANDOVER_MEMO_CONTENT).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("op2TransitionedEvent fromOpId=OP1, toOpId=OP2 수렴 검증")
    void op2TransitionedEventFromToOpIdsMatchSpec() {
      var event = OperationalPeriodFixtures.op2TransitionedEvent();
      assertThat(event.fromOpId()).isEqualTo(DemoScenarioFixtures.OP1_ID);
      assertThat(event.toOpId()).isEqualTo(DemoScenarioFixtures.OP2_ID);
    }

    @Test
    @DisplayName("SC-10 board convergence slot 이름이 spec 리터럴과 일치한다")
    void boardConvergenceSlotsMatchSpec() {
      assertThat(DemoScenarioFixtures.BOARD_OP_TOGGLE_SLOT).isEqualTo("op_toggle");
      assertThat(DemoScenarioFixtures.BOARD_OP_HISTORY_SLOT).isEqualTo("op_history");
      assertThat(DemoScenarioFixtures.BOARD_HANDOVER_STATUS_SLOT).isEqualTo("handover_status");
    }
  }

  // ── SC-11 OpenAI summary fixture 검증 ────────────────────────────────────

  @Nested
  @DisplayName("SC-11 search_history_summary OpenAI input fixture 유효성")
  class Sc11SummaryFixtureValidity {

    @Test
    @DisplayName("성공 evidence는 null이 아니고 금지 문구를 포함하지 않는다")
    void successEvidenceIsValidAndContainsNoForbiddenPhrase() {
      var guard = new ForbiddenSummaryGuard();

      assertThat(DemoScenarioFixtures.SUMMARY_EVIDENCE_SUCCESS).isNotNull().isNotBlank();
      assertThat(guard.containsForbiddenPhrase(DemoScenarioFixtures.SUMMARY_EVIDENCE_SUCCESS))
          .as("성공 evidence는 FR-23 금지 문구를 포함하면 안 된다")
          .isFalse();
    }

    @Test
    @DisplayName("실패 케이스 evidence는 FR-23 금지 문구를 포함한다 — Guard 트리거 확인")
    void failureEvidenceContainsForbiddenPhrase() {
      var guard = new ForbiddenSummaryGuard();

      assertThat(guard.containsForbiddenPhrase(DemoScenarioFixtures.SUMMARY_EVIDENCE_WITH_FORBIDDEN_PHRASE))
          .as("실패 evidence는 FR-23 금지 문구를 포함해야 Guard가 트리거된다")
          .isTrue();
    }

    @Test
    @DisplayName("ForbiddenSummaryGuard가 4개 금지 범주를 모두 개별적으로 감지한다")
    void forbiddenSummaryGuardDetectsAllFourCategories() {
      var guard = new ForbiddenSummaryGuard();
      assertThat(guard.containsForbiddenPhrase("다음 구역 추천")).isTrue();
      assertThat(guard.containsForbiddenPhrase("누락 확정")).isTrue();
      assertThat(guard.containsForbiddenPhrase("위험도 높음")).isTrue();
      assertThat(guard.containsForbiddenPhrase("자동 판단")).isTrue();
    }

    @Test
    @DisplayName("성공 evidence와 실패 evidence는 서로 다른 내용이다")
    void successAndFailureEvidenceAreDifferent() {
      assertThat(DemoScenarioFixtures.SUMMARY_EVIDENCE_SUCCESS)
          .isNotEqualTo(DemoScenarioFixtures.SUMMARY_EVIDENCE_WITH_FORBIDDEN_PHRASE);
    }
  }

  // ── manual data repair checklist 검증 ────────────────────────────────────

  @Nested
  @DisplayName("manual data repair checklist 유효성")
  class ManualRepairChecklistValidity {

    @Test
    @DisplayName("checklist가 비어 있지 않다")
    void checklistIsNotEmpty() {
      assertThat(DemoScenarioFixtures.MANUAL_REPAIR_CHECKLIST).isNotEmpty();
    }

    @Test
    @DisplayName("checklist 각 항목이 null이 아니고 비어 있지 않다")
    void checklistItemsAreNotBlank() {
      DemoScenarioFixtures.MANUAL_REPAIR_CHECKLIST.forEach(item ->
          assertThat(item).isNotNull().isNotBlank());
    }

    @Test
    @DisplayName("checklist가 OP1, OP2, HANDOVER_MEMO, SUMMARY 항목을 포함한다")
    void checklistCoversKeyDemoScenarioSteps() {
      String joined = String.join(" ", DemoScenarioFixtures.MANUAL_REPAIR_CHECKLIST);
      assertThat(joined).contains("OP1").contains("OP2").contains("HANDOVER_MEMO").contains("SUMMARY");
    }
  }
}
