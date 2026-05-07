package com.surimap.operationalperiod;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.operationalperiod.fixture.OpBootstrapFixtures;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.operationalperiod.testdouble.Op1BootstrapMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T05A: OP1 bootstrap completion과 current/list OP mock contract 테스트.
 *
 * <p>bootstrap handler 구현체가 없는 Phase 0 상태다. L1은 이 mock으로 INCIDENT_CREATED 후 OP1 completion과
 * current/list OP shape을 소비한다.
 */
@DisplayName("L3-T05A OP1 bootstrap mock contract")
class Op1BootstrapContractTest {

  private final Op1BootstrapMock bootstrapMock = new Op1BootstrapMock();

  @Test
  @DisplayName("INCIDENT_CREATED 처리 후 OP1 completion과 current/list OP를 제공한다")
  void incident_created_처리_후_op1_completion과_current_list_op를_제공한다() {
    OpBootstrapFixtures.Op1BootstrapCompletion completion =
        bootstrapMock.handle(OpBootstrapFixtures.incidentCreatedEvent());

    assertThat(completion.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(completion.opId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
    assertThat(completion.sequenceNo()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO);
    assertThat(completion.status()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_STATUS);
    assertThat(completion.version()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_VERSION);
    assertThat(completion.fromOpId()).isNull();
    assertThat(completion.toOpId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);

    CurrentOpResult currentOp = completion.currentOp();
    assertThat(currentOp.opId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
    assertThat(currentOp.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(currentOp.status()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_STATUS);
    assertThat(currentOp.sequenceNo()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO);
    assertThat(currentOp.startedAt()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_STARTED_AT);
    assertThat(currentOp.endedAt()).isNull();
    assertThat(currentOp.reason()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_REASON);
    assertThat(currentOp.version()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_VERSION);

    assertThat(completion.listRows()).hasSize(1);
    OperationalPeriodRow row = completion.listRows().get(0);
    assertThat(row.opId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
    assertThat(row.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(row.status()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_STATUS);
    assertThat(row.sequenceNo()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO);
    assertThat(row.startedAt()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_STARTED_AT);
    assertThat(row.endedAt()).isNull();
    assertThat(row.reason()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_REASON);
    assertThat(row.version()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_VERSION);
  }

  @Test
  @DisplayName("OP1 bootstrap completion payload는 fromOpId=null, toOpId=OP1이다")
  void op1_bootstrap_이벤트는_fromOpId가_null이다() {
    OperationalPeriodFixtures.OpTransitionedEvent event =
        bootstrapMock.handle(OpBootstrapFixtures.incidentCreatedEvent()).event();

    assertThat(event.type()).isEqualTo("OP_TRANSITIONED");
    assertThat(event.id()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
    assertThat(event.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(event.status()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_STATUS);
    assertThat(event.version()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_VERSION);
    assertThat(event.sequenceNo()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO);
    assertThat(event.opId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
    assertThat(event.fromOpId()).isNull();
    assertThat(event.toOpId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
    assertThat(event.eventId()).isNull();
  }

  @Test
  @DisplayName("중복 INCIDENT_CREATED는 같은 OP1 completion과 publish request 1회로 수렴한다")
  void 중복_incident_created는_같은_op1_completion과_publish_request_1회로_수렴한다() {
    OpBootstrapFixtures.Op1BootstrapCompletion first =
        bootstrapMock.handle(OpBootstrapFixtures.incidentCreatedEvent());
    OpBootstrapFixtures.Op1BootstrapCompletion second =
        bootstrapMock.handle(OpBootstrapFixtures.incidentCreatedEvent());

    assertThat(second).isEqualTo(first);
    assertThat(bootstrapMock.publishRequestCount()).isEqualTo(1);
  }
}
