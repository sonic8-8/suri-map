package com.surimap.operationalperiod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.operationalperiod.command.ManualOpTransitionRequest;
import com.surimap.operationalperiod.command.ManualOpTransitionResult;
import com.surimap.operationalperiod.command.OpTransitionedEvent;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures.ExpectedOpTransitionEvent;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.operationalperiod.testdouble.ManualOpTransitionMock;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T06A OP2+ 전환 RED 테스트.
 *
 * <p>완료 기준: web commander가 reason과 함께 OP2+를 열고 이전 active OP를 닫으며, transition history를 보존하고
 * OP_TRANSITIONED를 발행한다.
 *
 * <p>기준 문서: S8.json §acceptance_criteria AC-S8-02, §harness_fixtures.sc10_op_transition_convergence,
 * §domain_policies.op_transition
 */
@DisplayName("L3-T06A OP2+ 전환 RED 테스트")
class OpTransitionRedTest {

  private static final String IDEM_KEY = "idem-s8-op-transition-001";

  private final ManualOpTransitionMock mock = new ManualOpTransitionMock();

  // -------------------------------------------------------------------------
  // AC-S8-02: 이전 OP는 ENDED, 새 OP는 ACTIVE
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("RE_SEARCH reason으로 OP2 전환 시 새 OP는 ACTIVE sequenceNumber=2를 갖는다")
  void re_search_reason으로_op2_전환_시_새_op는_active_sequenceNumber_2를_갖는다() {
    ManualOpTransitionRequest request =
        new ManualOpTransitionRequest(
            OperationalPeriodFixtures.INCIDENT_ID, "RE_SEARCH", null, null, IDEM_KEY);

    ManualOpTransitionResult result = mock.transition(request);

    OperationalPeriodRow newOp = result.newOp();
    assertThat(newOp.opId()).isEqualTo(OperationalPeriodFixtures.OP2_ID);
    assertThat(newOp.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(newOp.status()).isEqualTo("ACTIVE");
    assertThat(newOp.sequenceNumber()).isEqualTo(2);
    assertThat(newOp.reason()).isEqualTo("RE_SEARCH");
    assertThat(newOp.version()).isEqualTo(1L);
  }

  @Test
  @DisplayName("OP2 전환 시 이전 OP1은 ENDED 상태가 되고 version이 증가한다")
  void op2_전환_시_이전_op1은_ended_상태가_되고_version이_증가한다() {
    ManualOpTransitionRequest request =
        new ManualOpTransitionRequest(
            OperationalPeriodFixtures.INCIDENT_ID, "AREA_CHANGED", null, null, IDEM_KEY);

    ManualOpTransitionResult result = mock.transition(request);

    OperationalPeriodRow previousOp = result.previousOp();
    assertThat(previousOp.opId()).isEqualTo(OperationalPeriodFixtures.OP1_ID);
    assertThat(previousOp.status()).isEqualTo("ENDED");
    assertThat(previousOp.version()).isGreaterThan(1L); // ENDED로 전환 시 version 증가
  }

  // -------------------------------------------------------------------------
  // domain_policies.op_transition: previousActiveOp automatically ENDED in same tx
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("사건 내 동시에 ACTIVE인 OP는 정확히 1개여야 한다 - 전환 후 이전 OP는 ENDED")
  void 사건내_동시에_active인_op는_정확히_1개여야_한다() {
    ManualOpTransitionRequest request =
        new ManualOpTransitionRequest(
            OperationalPeriodFixtures.INCIDENT_ID, "RE_SEARCH", null, null, IDEM_KEY);

    ManualOpTransitionResult result = mock.transition(request);

    assertThat(result.newOp().status()).isEqualTo("ACTIVE");
    assertThat(result.previousOp().status()).isEqualTo("ENDED");
    // exactly one ACTIVE: newOp != previousOp, and previousOp is not ACTIVE
    assertThat(result.newOp().opId()).isNotEqualTo(result.previousOp().opId());
  }

  // -------------------------------------------------------------------------
  // OP_TRANSITIONED publish request: fromOpId, toOpId
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("OP2 전환은 fromOpId=OP1 toOpId=OP2인 OP_TRANSITIONED 이벤트를 발행한다")
  void op2_전환은_op_transitioned_이벤트를_발행한다() {
    ManualOpTransitionRequest request =
        new ManualOpTransitionRequest(
            OperationalPeriodFixtures.INCIDENT_ID, "RE_SEARCH", null, null, IDEM_KEY);

    ManualOpTransitionResult result = mock.transition(request);

    OpTransitionedEvent event = result.publishedEvent();
    assertThat(event.type()).isEqualTo("OP_TRANSITIONED");
    assertThat(event.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(event.opId()).isEqualTo(OperationalPeriodFixtures.OP2_ID);
    assertThat(event.fromOpId()).isEqualTo(OperationalPeriodFixtures.OP1_ID);
    assertThat(event.toOpId()).isEqualTo(OperationalPeriodFixtures.OP2_ID);
    assertThat(event.status()).isEqualTo("ACTIVE");
    assertThat(event.version()).isEqualTo(1L);
    assertThat(event.sequenceNumber()).isEqualTo(2);
  }

  @Test
  @DisplayName("OP_TRANSITIONED 이벤트 payload는 SC-10 fixture 수렴 기준과 일치한다")
  void op_transitioned_payload는_sc10_fixture_수렴_기준과_일치한다() {
    ManualOpTransitionRequest request =
        new ManualOpTransitionRequest(
            OperationalPeriodFixtures.INCIDENT_ID, "RE_SEARCH", null, null, IDEM_KEY);

    ManualOpTransitionResult result = mock.transition(request);
    ExpectedOpTransitionEvent expected = OperationalPeriodFixtures.op2TransitionedEvent();

    OpTransitionedEvent event = result.publishedEvent();
    assertThat(event.type()).isEqualTo(expected.type());
    assertThat(event.incidentId()).isEqualTo(expected.incidentId());
    assertThat(event.opId()).isEqualTo(expected.opId());
    assertThat(event.status()).isEqualTo(expected.payloadStatus());
    assertThat(event.version()).isEqualTo(expected.payloadVersion());
    assertThat(event.sequenceNumber()).isEqualTo(expected.sequenceNumber());
    assertThat(event.fromOpId()).isEqualTo(expected.fromOpId());
    assertThat(event.toOpId()).isEqualTo(expected.toOpId());
  }

  // -------------------------------------------------------------------------
  // transition history 보존
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("전환 이후 transition history에 결과가 기록된다")
  void 전환_이후_transition_history에_결과가_기록된다() {
    ManualOpTransitionRequest request =
        new ManualOpTransitionRequest(
            OperationalPeriodFixtures.INCIDENT_ID, "RE_SEARCH", null, null, IDEM_KEY);

    mock.transition(request);

    assertThat(mock.transitions()).hasSize(1);
    ManualOpTransitionResult recorded = mock.transitions().get(0);
    assertThat(recorded.newOp().opId()).isEqualTo(OperationalPeriodFixtures.OP2_ID);
    assertThat(recorded.previousOp().opId()).isEqualTo(OperationalPeriodFixtures.OP1_ID);
  }

  // -------------------------------------------------------------------------
  // idempotency: same key replays cached response
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("같은 Idempotency-Key로 두 번 전환하면 cached row와 event를 그대로 반환한다")
  void 같은_idempotency_key로_두_번_전환하면_cached_결과를_반환한다() {
    ManualOpTransitionRequest request =
        new ManualOpTransitionRequest(
            OperationalPeriodFixtures.INCIDENT_ID, "RE_SEARCH", null, null, IDEM_KEY);

    ManualOpTransitionResult first = mock.transition(request);
    ManualOpTransitionResult second = mock.transition(request);

    assertThat(second.newOp()).isEqualTo(first.newOp());
    assertThat(second.previousOp()).isEqualTo(first.previousOp());
    assertThat(second.publishedEvent()).isEqualTo(first.publishedEvent());
    // transition history에는 1건만 기록 (replay는 새 row/event를 생성하지 않음)
    assertThat(mock.transitions()).hasSize(1);
  }

  // -------------------------------------------------------------------------
  // reason enum 검증 — OTHER reason은 reasonMemo 없이 요청 시 도메인 거부
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("OTHER reason으로 reasonMemo 없이 전환 시 IllegalArgumentException이 발생한다")
  void other_reason_reasonmemo_없이_전환_시_예외가_발생한다() {
    // reasonMemo가 null인 OTHER reason 요청 → ManualOpTransitionRequest 생성 시점에 domain policy 위반
    assertThatThrownBy(
            () ->
                new ManualOpTransitionRequest(
                    OperationalPeriodFixtures.INCIDENT_ID, "OTHER", null, null, "idem-other-no-memo"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reasonMemo");
  }

  // -------------------------------------------------------------------------
  // unknown incidentId guard
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("알 수 없는 incidentId로 전환 요청 시 IllegalArgumentException이 발생한다")
  void 알_수_없는_incidentId로_전환_요청_시_예외가_발생한다() {
    ManualOpTransitionRequest request =
        new ManualOpTransitionRequest(
            UUID.randomUUID(), "RE_SEARCH", null, null, "idem-unknown-incident");

    assertThatThrownBy(() -> mock.transition(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("unknown incidentId:");
    assertThat(mock.transitions()).isEmpty();
  }

  // -------------------------------------------------------------------------
  // publish failure: event 발행 실패 시 row와 event 모두 absent
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("전환 실패 주입 시 transition history와 publishedEvent가 모두 없다")
  void 전환_실패_주입_시_history와_event가_모두_없다() {
    String failKey = "idem-s8-op-transition-fail";
    mock.failTransitionFor(failKey, new RuntimeException("publish_failure_injected"));

    ManualOpTransitionRequest request =
        new ManualOpTransitionRequest(
            OperationalPeriodFixtures.INCIDENT_ID, "RE_SEARCH", null, null, failKey);

    assertThatThrownBy(() -> mock.transition(request))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("publish_failure_injected");
    assertThat(mock.transitions()).isEmpty();
  }
}
