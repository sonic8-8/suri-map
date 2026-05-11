package com.surimap.operationalperiod.fixturetest;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.operationalperiod.fixture.CurrentOpConsumerFixtures;
import com.surimap.operationalperiod.fixture.CurrentOpGuardFixtures;
import com.surimap.operationalperiod.fixture.OpBootstrapFixtures;
import com.surimap.operationalperiod.fixture.OpTransitionFixtures;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.fixture.S8IdempotencyFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * operationalperiod fixture exactness 테스트.
 *
 * <p>maparea/fixturetest와 같은 역할로, fixture 값이 문서/계약과 어긋나지 않는지 확인한다.
 */
@DisplayName("operationalperiod fixture exactness")
class OperationalPeriodFixtureExactnessTest {

  @Test
  @DisplayName("공통 OP fixture 값이 고정되어 있다")
  void 공통_op_fixture_값이_고정되어_있다() {
    assertThat(OperationalPeriodFixtures.INCIDENT_ALIAS).isEqualTo("inc-precinct-first-001");
    assertThat(OperationalPeriodFixtures.INCIDENT_ID).isNotNull();

    assertThat(OperationalPeriodFixtures.CURRENT_OP_ALIAS).isEqualTo("op-precinct-001-op1");
    assertThat(OperationalPeriodFixtures.CURRENT_OP_STATUS).isEqualTo("ACTIVE");
    assertThat(OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO).isEqualTo(1);
    assertThat(OperationalPeriodFixtures.CURRENT_OP_VERSION).isEqualTo(1L);
    assertThat(OperationalPeriodFixtures.CURRENT_OP_REASON).isEqualTo("INITIAL");

    assertThat(OperationalPeriodFixtures.NEW_OP_ALIAS).isEqualTo("op-precinct-001-op2");
    assertThat(OperationalPeriodFixtures.NEW_OP_SEQUENCE_NO).isEqualTo(2);
    assertThat(OperationalPeriodFixtures.NEW_OP_REASON).isEqualTo("RE_SEARCH");

    assertThat(OperationalPeriodFixtures.OP_STATUSES).containsExactly("ACTIVE", "ENDED");
    assertThat(OperationalPeriodFixtures.OP_REASON_PERSISTED)
        .containsExactly("INITIAL", "RE_SEARCH", "AREA_CHANGED", "OTHER");
    assertThat(OperationalPeriodFixtures.OP_REASON_MANUAL_CREATE)
        .containsExactly("RE_SEARCH", "AREA_CHANGED", "OTHER");
  }

  @Test
  @DisplayName("bootstrap fixture 값이 고정되어 있다")
  void bootstrap_fixture_값이_고정되어_있다() {
    var incidentCreated = OpBootstrapFixtures.incidentCreatedEvent();
    assertThat(incidentCreated.type()).isEqualTo("INCIDENT_CREATED");
    assertThat(incidentCreated.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);

    var completion = OpBootstrapFixtures.op1BootstrapCompletion();
    assertThat(completion.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(completion.opId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
    assertThat(completion.sequenceNo()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO);
    assertThat(completion.status()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_STATUS);
    assertThat(completion.version()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_VERSION);
    assertThat(completion.fromOpId()).isNull();
    assertThat(completion.toOpId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
    assertThat(completion.currentOp()).isEqualTo(OperationalPeriodFixtures.currentOpResult());
    assertThat(completion.listRows()).containsExactly(OperationalPeriodFixtures.currentOpListRow());
    assertThat(completion.event()).isEqualTo(OpBootstrapFixtures.op1BootstrappedEvent());
  }

  @Test
  @DisplayName("guard, consumer, transition, idempotency fixture 값이 고정되어 있다")
  void guard_consumer_transition_idempotency_fixture_값이_고정되어_있다() {
    assertThat(CurrentOpGuardFixtures.OP_REQUIRED_ERROR).isEqualTo("op_required");
    assertThat(CurrentOpGuardFixtures.OP_MISMATCH_ERROR).isEqualTo("op_mismatch");
    assertThat(CurrentOpGuardFixtures.opRequiredGuard().error()).isEqualTo("op_required");
    assertThat(CurrentOpGuardFixtures.opRequiredGuard().status()).isEqualTo(409);
    assertThat(CurrentOpGuardFixtures.opMismatchGuard().error()).isEqualTo("op_mismatch");
    assertThat(CurrentOpGuardFixtures.opMismatchGuard().status()).isEqualTo(409);

    assertThat(CurrentOpConsumerFixtures.CURRENT_OP_CONSUMERS)
        .containsExactly(
            "S7 offline manifest builder",
            "S3-1 search session/path write",
            "S5 marker/support/person write");
    assertThat(CurrentOpConsumerFixtures.FORBIDDEN_S8_OWNER_WRITES)
        .containsExactly(
            "offline_package_manifest",
            "offline_package_status",
            "search_session",
            "search_path",
            "path_segment",
            "marker",
            "photo",
            "notification_delivery");

    assertThat(OpTransitionFixtures.SC10_BOARD_PROBE_SLOTS)
        .containsExactly("op_toggle", "op_history", "handover_status");
    assertThat(OpTransitionFixtures.SC10_HANDOVER_STATUS).isEqualTo("NEEDS_MEMO");
    assertThat(OpTransitionFixtures.PRE_CONVERGENCE_STATE).isEqualTo("PENDING_PROJECTION");
    assertThat(OpTransitionFixtures.opTransitionedEvent().type()).isEqualTo("OP_TRANSITIONED");

    assertThat(S8IdempotencyFixtures.IDEMPOTENCY_KEY).isEqualTo("idem-s8-op-transition-001");
    assertThat(S8IdempotencyFixtures.IDEMPOTENCY_MISMATCH_ERROR).isEqualTo("idempotency_mismatch");
    assertThat(S8IdempotencyFixtures.IDEMPOTENT_COVERED_ENDPOINTS)
        .containsExactly(
            "POST /operational-periods",
            "POST /operational-periods/{opId}/assignments",
            "POST /handover-memos",
            "PATCH /duty-shifts/{dutyShiftId}");
  }
}
