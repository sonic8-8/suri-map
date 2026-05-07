package com.surimap.operationalperiod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.surimap.operationalperiod.fixture.CurrentOpGuardFixtures;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.testdouble.CurrentOpGuardMock;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T05A: @RequireCurrentOp 실패 응답 fixture 계약 테스트.
 *
 * <p>실제 annotation/AOP 구현은 L3-T05B 이후에 추가한다.
 */
@DisplayName("L3-T05A current OP guard failure contract")
class CurrentOpGuardContractTest {

  private final OperationalPeriodQuery opQuery = mock(OperationalPeriodQuery.class);
  private final CurrentOpGuardMock guardMock = new CurrentOpGuardMock(opQuery);

  @Test
  @DisplayName("current OP가 없을 때 write는 op_required 409로 거부된다")
  void current_op_없으면_op_required_409로_거부된다() {
    when(opQuery.current(OperationalPeriodFixtures.INCIDENT_ID)).thenReturn(Optional.empty());

    CurrentOpGuardFixtures.CurrentOpGuardDecision decision =
        guardMock.requireCurrentOp(
            OperationalPeriodFixtures.INCIDENT_ID, OperationalPeriodFixtures.CURRENT_OP_ID);

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.currentOpId()).isNull();
    assertThat(decision.error()).isEqualTo(CurrentOpGuardFixtures.OP_REQUIRED_ERROR);
    assertThat(decision.status()).isEqualTo(409);
  }

  @Test
  @DisplayName("write 요청 opId가 current OP와 다르면 op_mismatch 409로 거부된다")
  void opId_불일치하면_op_mismatch_409로_거부된다() {
    when(opQuery.current(OperationalPeriodFixtures.INCIDENT_ID))
        .thenReturn(Optional.of(OperationalPeriodFixtures.currentOpResult()));

    CurrentOpGuardFixtures.CurrentOpGuardDecision decision =
        guardMock.requireCurrentOp(
            OperationalPeriodFixtures.INCIDENT_ID, OperationalPeriodFixtures.NEW_OP_ID);

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.currentOpId()).isNull();
    assertThat(decision.error()).isEqualTo(CurrentOpGuardFixtures.OP_MISMATCH_ERROR);
    assertThat(decision.status()).isEqualTo(409);
  }

  @Test
  @DisplayName("write 요청 opId가 current OP와 같으면 guard를 통과한다")
  void opId_일치하면_guard를_통과한다() {
    when(opQuery.current(OperationalPeriodFixtures.INCIDENT_ID))
        .thenReturn(Optional.of(OperationalPeriodFixtures.currentOpResult()));

    CurrentOpGuardFixtures.CurrentOpGuardDecision decision =
        guardMock.requireCurrentOp(
            OperationalPeriodFixtures.INCIDENT_ID, OperationalPeriodFixtures.CURRENT_OP_ID);

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.currentOpId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
    assertThat(decision.error()).isNull();
    assertThat(decision.status()).isEqualTo(200);
  }
}
