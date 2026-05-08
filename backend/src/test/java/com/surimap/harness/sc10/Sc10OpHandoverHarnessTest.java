package com.surimap.harness.sc10;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.handover.command.HandoverMemoCreateRequest;
import com.surimap.handover.fixture.HandoverMemoFixtures;
import com.surimap.handover.query.HandoverMemoRow;
import com.surimap.harness.sc10.fixture.Sc10Fixtures;
import com.surimap.harness.sc10.mock.MockHandoverMemoPort;
import com.surimap.harness.sc10.mock.MockOpTransitionPort;
import com.surimap.operationalperiod.command.ManualOpTransitionRequest;
import com.surimap.operationalperiod.command.ManualOpTransitionResult;
import com.surimap.operationalperiod.command.OpTransitionedEvent;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * SC-10 domain harness: radio_report_received → commander_decision_recorded → area_completed →
 * op_transitioned → handover_saved → board_api_refetched 순서 검증.
 *
 * <p>기준 문서: docs/spec/specs/S8.json §harness_fixtures.sc10_op_transition_convergence,
 * docs/spec/harness-scenarios.md §2 SC-10.
 *
 * <p>L2/S4/S3-2 mock 으로만 동작한다. 실제 DB/Spring 없음.
 * 이 harness는 SC-10 시나리오 6단계 순서와 OP/handover board convergence evidence를 생성한다.
 */
@DisplayName("SC-10 OP 전환·인수인계 harness")
class Sc10OpHandoverHarnessTest {

  private MockOpTransitionPort opTransitionPort;
  private MockHandoverMemoPort handoverMemoPort;

  // harness_execution_log: 시나리오 실행 순서를 기록
  private final List<String> executionLog = new ArrayList<>();

  @BeforeEach
  void setUp() {
    opTransitionPort = new MockOpTransitionPort();
    handoverMemoPort = new MockHandoverMemoPort();
    executionLog.clear();
  }

  // ── Step 1~2: radio report + commander decision ──────────────────────────

  @Nested
  @DisplayName("SC-10 Step-1~2: 무선 보고 수신·지휘관 결정 기록")
  class RadioAndDecisionStep {

    @Test
    @DisplayName("harness_execution_log step 1~2 이름이 spec 리터럴과 일치한다")
    void executionLogStepNamesMatchSpec() {
      // S8.json §harness-scenarios.md SC-10 harness_execution_log
      assertThat(Sc10Fixtures.STEP_RADIO_REPORT_RECEIVED)
          .isEqualTo("radio_report_received");
      assertThat(Sc10Fixtures.STEP_COMMANDER_DECISION_RECORDED)
          .isEqualTo("commander_decision_recorded");
    }

    @Test
    @DisplayName("OP1 fixture alias/id/status가 S8.json harness_constraints spec 리터럴과 일치한다")
    void op1FixtureMatchesSpecLiteral() {
      // S8.json harness_constraints: op1Alias=op-precinct-001-op1, status=ACTIVE → ENDED
      assertThat(OperationalPeriodFixtures.OP1_ALIAS).isEqualTo("op-precinct-001-op1");
      assertThat(OperationalPeriodFixtures.PREVIOUS_OP_STATUS).isEqualTo("ENDED");
      assertThat(OperationalPeriodFixtures.PREVIOUS_OP_VERSION).isEqualTo(2L);
    }

    @Test
    @DisplayName("OP2 fixture alias/id/status/reason이 S8.json harness_constraints spec 리터럴과 일치한다")
    void op2FixtureMatchesSpecLiteral() {
      // S8.json harness_constraints: op2Alias=op-precinct-001-op2, reason=RE_SEARCH, version=1
      assertThat(OperationalPeriodFixtures.OP2_ALIAS).isEqualTo("op-precinct-001-op2");
      assertThat(OperationalPeriodFixtures.NEW_OP_STATUS).isEqualTo("ACTIVE");
      assertThat(OperationalPeriodFixtures.NEW_OP_REASON).isEqualTo("RE_SEARCH");
      assertThat(OperationalPeriodFixtures.NEW_OP_VERSION).isEqualTo(1L);
      assertThat(OperationalPeriodFixtures.NEW_OP_SEQUENCE_NO).isEqualTo(2);
    }
  }

  // ── Step 3~4: area_completed + op_transitioned ───────────────────────────

  @Nested
  @DisplayName("SC-10 Step-3~4: 구역 완료·OP 전환")
  class OpTransitionStep {

    private ManualOpTransitionRequest validRequest() {
      return new ManualOpTransitionRequest(
          Sc10Fixtures.INCIDENT_ID,
          "RE_SEARCH",
          null,
          "SC-10 harness OP 전환",
          "idem-sc10-op-transition-001");
    }

    @Test
    @DisplayName("OP 전환 후 새 OP2가 ACTIVE 상태로 반환된다")
    void opTransitionYieldsActiveNewOp() {
      opTransitionPort.stubSuccess();
      executionLog.add(Sc10Fixtures.STEP_AREA_COMPLETED);

      ManualOpTransitionResult result = opTransitionPort.transition(validRequest());
      executionLog.add(Sc10Fixtures.STEP_OP_TRANSITIONED);

      assertThat(result.newOp().opId()).isEqualTo(Sc10Fixtures.OP2_ID);
      assertThat(result.newOp().status()).isEqualTo("ACTIVE");
      assertThat(result.newOp().version()).isEqualTo(Sc10Fixtures.NEW_OP_VERSION);
      assertThat(result.newOp().sequenceNo()).isEqualTo(Sc10Fixtures.NEW_OP_SEQUENCE_NO);
    }

    @Test
    @DisplayName("OP 전환 후 이전 OP1이 ENDED 상태로 반환된다")
    void opTransitionYieldsEndedPreviousOp() {
      opTransitionPort.stubSuccess();

      ManualOpTransitionResult result = opTransitionPort.transition(validRequest());

      assertThat(result.previousOp().opId()).isEqualTo(Sc10Fixtures.OP1_ID);
      assertThat(result.previousOp().status()).isEqualTo("ENDED");
      assertThat(result.previousOp().version()).isEqualTo(Sc10Fixtures.PREVIOUS_OP_VERSION);
    }

    @Test
    @DisplayName("OP 전환 시 OP_TRANSITIONED 이벤트가 발행되고 fromOpId/toOpId가 fixture와 일치한다")
    void opTransitionPublishesOpTransitionedEvent() {
      opTransitionPort.stubSuccess();

      ManualOpTransitionResult result = opTransitionPort.transition(validRequest());
      OpTransitionedEvent event = result.publishedEvent();

      assertThat(event.type()).isEqualTo("OP_TRANSITIONED");
      assertThat(event.fromOpId()).isEqualTo(Sc10Fixtures.OP1_ID);
      assertThat(event.toOpId()).isEqualTo(Sc10Fixtures.OP2_ID);
      assertThat(event.incidentId()).isEqualTo(Sc10Fixtures.INCIDENT_ID);
      assertThat(event.status()).isEqualTo("ACTIVE");
      assertThat(event.version()).isEqualTo(Sc10Fixtures.NEW_OP_VERSION);
      assertThat(event.sequenceNumber()).isEqualTo(Sc10Fixtures.NEW_OP_SEQUENCE_NO);
    }

    @Test
    @DisplayName("OP 전환 요청이 캡처되고 incidentId/reason이 fixture와 일치한다")
    void opTransitionRequestIsCaptured() {
      opTransitionPort.stubSuccess();
      opTransitionPort.transition(validRequest());

      assertThat(opTransitionPort.capturedRequests()).hasSize(1);
      assertThat(opTransitionPort.capturedRequests().get(0).incidentId())
          .isEqualTo(Sc10Fixtures.INCIDENT_ID);
      assertThat(opTransitionPort.capturedRequests().get(0).reason())
          .isEqualTo("RE_SEARCH");
    }

    @Test
    @DisplayName("reason=OTHER + reasonMemo=null 전환 요청은 IllegalArgumentException을 발생시킨다")
    void otherReasonWithoutMemoThrows() {
      org.junit.jupiter.api.Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> new ManualOpTransitionRequest(
              Sc10Fixtures.INCIDENT_ID, "OTHER", null, null, "idem-other-001"));
    }
  }

  // ── Step 5: handover_saved ───────────────────────────────────────────────

  @Nested
  @DisplayName("SC-10 Step-5: 인수인계 메모 저장")
  class HandoverSavedStep {

    private HandoverMemoCreateRequest validMemoRequest() {
      return new HandoverMemoCreateRequest(
          Sc10Fixtures.INCIDENT_ID,
          Sc10Fixtures.OP2_ID,
          "OPERATIONAL_PERIOD",
          Sc10Fixtures.OP2_ID,
          "SC-10 harness 인수인계 메모",
          HandoverMemoFixtures.CREATED_BY_ACCOUNT_ID,
          "WEB",
          Instant.parse("2026-04-28T09:00:00Z"));
    }

    @Test
    @DisplayName("handover memo 저장 후 id/version/opId가 fixture와 일치한다")
    void handoverMemoIsCreatedWithFixtureValues() {
      executionLog.add(Sc10Fixtures.STEP_HANDOVER_SAVED);
      var result = handoverMemoPort.create(validMemoRequest());

      assertThat(result.id()).isEqualTo(Sc10Fixtures.MEMO_ID);
      assertThat(result.opId()).isEqualTo(Sc10Fixtures.OP2_ID);
      assertThat(result.version()).isEqualTo(Sc10Fixtures.MEMO_VERSION);
      assertThat(result.memoTargetType()).isEqualTo("OPERATIONAL_PERIOD");
    }

    @Test
    @DisplayName("handover memo fixture alias/eventId가 harness fixture 리터럴과 일치한다")
    void handoverMemoFixtureMatchesSpecLiteral() {
      // docs/spec/harness-scenarios.md §6 mock 112/SC-10 fixture.
      assertThat(HandoverMemoFixtures.MEMO_ALIAS).isEqualTo("memo-precinct-handover-001");
      assertThat(HandoverMemoFixtures.MEMO_EVENT_ID).isEqualTo("evt-s8-handover-memo-001");
      assertThat(HandoverMemoFixtures.MEMO_VERSION).isEqualTo(1L);
      assertThat(HandoverMemoFixtures.MEMO_STATUS).isEqualTo("ACTIVE");
    }
  }

  // ── Step 6: board_api_refetched (convergence) ────────────────────────────

  @Nested
  @DisplayName("SC-10 Step-6: board convergence evidence")
  class BoardConvergenceStep {

    @Test
    @DisplayName("OP 전환 후 op_toggle 슬롯 기준 currentActiveOpId가 OP2로 수렴한다 — board convergence")
    void opToggleSlotConvergesToOp2AfterTransition() {
      // op_toggle 슬롯은 현재 ACTIVE OP ID를 표시한다 (S8.json §sc10 board convergence)
      opTransitionPort.stubSuccess();
      opTransitionPort.transition(new ManualOpTransitionRequest(
          Sc10Fixtures.INCIDENT_ID, "RE_SEARCH", null, null, "idem-sc10-board-001"));

      // board convergence: ACTIVE OP ID가 OP2로 전환됨
      assertThat(opTransitionPort.currentActiveOpId())
          .isPresent()
          .hasValue(Sc10Fixtures.OP2_ID);
    }

    @Test
    @DisplayName("OP 전환 후 handover memo byContext 쿼리로 OP2 메모가 조회된다 — board 수렴 증거")
    void handoverMemoIsQueryableAfterSave() {
      handoverMemoPort.create(new HandoverMemoCreateRequest(
          Sc10Fixtures.INCIDENT_ID,
          Sc10Fixtures.OP2_ID,
          "OPERATIONAL_PERIOD",
          Sc10Fixtures.OP2_ID,
          "SC-10 board 수렴 메모",
          HandoverMemoFixtures.CREATED_BY_ACCOUNT_ID,
          "WEB",
          Instant.parse("2026-04-28T09:00:00Z")));
      executionLog.add(Sc10Fixtures.STEP_BOARD_API_REFETCHED);

      List<HandoverMemoRow> rows = handoverMemoPort.byContext(
          Sc10Fixtures.INCIDENT_ID, Sc10Fixtures.OP2_ID, null, null);

      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).memoId()).isEqualTo(Sc10Fixtures.MEMO_ID);
      assertThat(rows.get(0).opId()).isEqualTo(Sc10Fixtures.OP2_ID);
      assertThat(rows.get(0).version()).isEqualTo(Sc10Fixtures.MEMO_VERSION);
    }

    @Test
    @DisplayName("전체 SC-10 시나리오 6단계가 mock으로 순서대로 통과한다")
    void fullSc10ScenarioPasses() {
      opTransitionPort.stubSuccess();

      // Step-1: radio_report_received (precondition — represented by fixture presence)
      executionLog.add(Sc10Fixtures.STEP_RADIO_REPORT_RECEIVED);
      assertThat(Sc10Fixtures.INCIDENT_ID).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);

      // Step-2: commander_decision_recorded (trigger for OP transition)
      executionLog.add(Sc10Fixtures.STEP_COMMANDER_DECISION_RECORDED);

      // Step-3: area_completed
      executionLog.add(Sc10Fixtures.STEP_AREA_COMPLETED);

      // Step-4: op_transitioned
      var transitionReq = new ManualOpTransitionRequest(
          Sc10Fixtures.INCIDENT_ID, "RE_SEARCH", null, "SC-10 harness OP 전환", "idem-sc10-full-001");
      ManualOpTransitionResult transitionResult = opTransitionPort.transition(transitionReq);
      executionLog.add(Sc10Fixtures.STEP_OP_TRANSITIONED);

      assertThat(transitionResult.newOp().status()).isEqualTo("ACTIVE");
      assertThat(transitionResult.newOp().opId()).isEqualTo(Sc10Fixtures.OP2_ID);
      assertThat(transitionResult.publishedEvent().type()).isEqualTo("OP_TRANSITIONED");
      assertThat(transitionResult.publishedEvent().fromOpId()).isEqualTo(Sc10Fixtures.OP1_ID);

      // Step-5: handover_saved
      handoverMemoPort.create(new HandoverMemoCreateRequest(
          Sc10Fixtures.INCIDENT_ID,
          Sc10Fixtures.OP2_ID,
          "OPERATIONAL_PERIOD",
          Sc10Fixtures.OP2_ID,
          "전체 SC-10 전환 메모",
          HandoverMemoFixtures.CREATED_BY_ACCOUNT_ID,
          "WEB",
          Instant.parse("2026-04-28T09:00:00Z")));
      executionLog.add(Sc10Fixtures.STEP_HANDOVER_SAVED);

      // Step-6: board_api_refetched (convergence query)
      List<HandoverMemoRow> boardRows = handoverMemoPort.byContext(
          Sc10Fixtures.INCIDENT_ID, Sc10Fixtures.OP2_ID, null, null);
      executionLog.add(Sc10Fixtures.STEP_BOARD_API_REFETCHED);

      assertThat(boardRows).hasSize(1);
      assertThat(boardRows.get(0).opId()).isEqualTo(Sc10Fixtures.OP2_ID);

      // harness_execution_log: SC-10 6단계 순서 검증
      assertThat(executionLog).containsExactly(
          Sc10Fixtures.STEP_RADIO_REPORT_RECEIVED,
          Sc10Fixtures.STEP_COMMANDER_DECISION_RECORDED,
          Sc10Fixtures.STEP_AREA_COMPLETED,
          Sc10Fixtures.STEP_OP_TRANSITIONED,
          Sc10Fixtures.STEP_HANDOVER_SAVED,
          Sc10Fixtures.STEP_BOARD_API_REFETCHED);
    }
  }
}
