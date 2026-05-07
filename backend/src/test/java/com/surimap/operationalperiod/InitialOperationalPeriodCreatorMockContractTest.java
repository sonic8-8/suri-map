package com.surimap.operationalperiod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.operationalperiod.command.InitialOperationalPeriodResult;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures.ExpectedOpTransitionEvent;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.operationalperiod.testdouble.InitialOperationalPeriodCreatorMock;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T05A OP1 자동 생성 handler mock contract 테스트.
 *
 * <p>S1-1 import transaction은 이 mock으로 OP1 생성과 OP_TRANSITIONED(from=null,to=OP1) capture를 검증한다.
 */
@DisplayName("L3-T05A InitialOperationalPeriodCreator OP1 mock contract")
class InitialOperationalPeriodCreatorMockContractTest {

  @Test
  @DisplayName("known incident import는 OP1 ACTIVE row를 생성한다")
  void known_incident_import는_op1_active_row를_생성한다() {
    InitialOperationalPeriodCreatorMock mock = new InitialOperationalPeriodCreatorMock();

    InitialOperationalPeriodResult result = mock.createOp1(OperationalPeriodFixtures.INCIDENT_ID);

    OperationalPeriodRow row = result.operationalPeriod();
    assertThat(row.opId()).isEqualTo(OperationalPeriodFixtures.OP1_ID);
    assertThat(row.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(row.status()).isEqualTo("ACTIVE");
    assertThat(row.reason()).isEqualTo("INITIAL");
    assertThat(row.sequenceNumber()).isEqualTo(1);
    assertThat(row.version()).isEqualTo(1L);
  }

  @Test
  @DisplayName("OP1 자동 생성 mock은 incidentId 기준 idempotent이다")
  void op1_자동_생성_mock은_incidentId_기준_idempotent이다() {
    InitialOperationalPeriodCreatorMock mock = new InitialOperationalPeriodCreatorMock();

    InitialOperationalPeriodResult first = mock.createOp1(OperationalPeriodFixtures.INCIDENT_ID);
    InitialOperationalPeriodResult second = mock.createOp1(OperationalPeriodFixtures.INCIDENT_ID);

    assertThat(second.operationalPeriod()).isEqualTo(first.operationalPeriod());
    assertThat(mock.createdRows()).containsExactly(OperationalPeriodFixtures.currentOp1());
    assertThat(mock.publishedEvents())
        .containsExactly(OperationalPeriodFixtures.op1TransitionedEvent());
  }

  @Test
  @DisplayName("OP1 자동 생성은 OP_TRANSITIONED from=null,to=OP1 이벤트를 capture한다")
  void op1_자동_생성은_op_transitioned_event를_capture한다() {
    InitialOperationalPeriodCreatorMock mock = new InitialOperationalPeriodCreatorMock();

    mock.createOp1(OperationalPeriodFixtures.INCIDENT_ID);

    ExpectedOpTransitionEvent event = mock.publishedEvents().get(0);
    assertThat(event.type()).isEqualTo("OP_TRANSITIONED");
    assertThat(event.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(event.payloadId()).isEqualTo(OperationalPeriodFixtures.OP1_ID);
    assertThat(event.opId()).isEqualTo(OperationalPeriodFixtures.OP1_ID);
    assertThat(event.fromOpId()).isNull();
    assertThat(event.toOpId()).isEqualTo(OperationalPeriodFixtures.OP1_ID);
    assertThat(event.payloadStatus()).isEqualTo("ACTIVE");
    assertThat(event.payloadVersion()).isEqualTo(1L);
    assertThat(event.sequenceNumber()).isEqualTo(1);
  }

  @Test
  @DisplayName("valid incident OP1 생성 실패 fixture는 row/event 없이 rollback 소비 예외를 노출한다")
  void valid_incident_op1_생성_실패_fixture는_row_event_없이_rollback_소비_예외를_노출한다() {
    InitialOperationalPeriodCreatorMock mock = new InitialOperationalPeriodCreatorMock();
    RuntimeException failure = OperationalPeriodFixtures.op1CreationFailure();

    mock.failOp1CreationFor(OperationalPeriodFixtures.INCIDENT_ID, failure);

    assertThatThrownBy(() -> mock.createOp1(OperationalPeriodFixtures.INCIDENT_ID))
        .isSameAs(failure)
        .hasMessage("op1_creation_failed");
    assertThat(mock.createdRows()).isEmpty();
    assertThat(mock.publishedEvents()).isEmpty();
  }

  @Test
  @DisplayName("unknown incident guard는 valid incident failure injection과 구분된다")
  void unknown_incident_guard는_valid_incident_failure_injection과_구분된다() {
    InitialOperationalPeriodCreatorMock mock = new InitialOperationalPeriodCreatorMock();

    mock.failOp1CreationFor(
        OperationalPeriodFixtures.INCIDENT_ID, OperationalPeriodFixtures.op1CreationFailure());

    assertThatThrownBy(() -> mock.createOp1(UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("unknown incidentId:");
    assertThat(mock.createdRows()).isEmpty();
    assertThat(mock.publishedEvents()).isEmpty();
  }
}
