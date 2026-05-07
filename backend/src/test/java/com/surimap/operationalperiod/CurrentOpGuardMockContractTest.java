package com.surimap.operationalperiod;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.operationalperiod.fixture.CurrentOpGuardFixtures;
import com.surimap.operationalperiod.fixture.CurrentOpGuardFixtures.CurrentOpGuardDecision;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.testdouble.CurrentOpGuardMock;
import com.surimap.operationalperiod.testdouble.OperationalPeriodQueryMock;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T05A @RequireCurrentOp guard failure mock contract 테스트.
 *
 * <p>real guard annotation 없이 소비 Lane이 op_required/op_mismatch 실패 코드를 검증할 수 있다.
 */
@DisplayName("L3-T05A @RequireCurrentOp mock guard contract")
class CurrentOpGuardMockContractTest {

  private final CurrentOpGuardMock guard = new CurrentOpGuardMock(new OperationalPeriodQueryMock());

  @Test
  @DisplayName("current OP가 없으면 409 op_required로 실패한다")
  void current_op가_없으면_op_required로_실패한다() {
    UUID unknownIncidentId = UUID.randomUUID();

    CurrentOpGuardDecision decision =
        guard.requireCurrentOp(unknownIncidentId, OperationalPeriodFixtures.CURRENT_OP_ID);

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.error()).isEqualTo(CurrentOpGuardFixtures.OP_REQUIRED_ERROR);
    assertThat(decision.status()).isEqualTo(409);
  }

  @Test
  @DisplayName("payload OP와 서버 current OP가 다르면 409 op_mismatch로 실패한다")
  void payload_op와_current_op가_다르면_op_mismatch로_실패한다() {
    CurrentOpGuardDecision decision =
        guard.requireCurrentOp(
            OperationalPeriodFixtures.INCIDENT_ID, OperationalPeriodFixtures.NEW_OP_ID);

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.error()).isEqualTo(CurrentOpGuardFixtures.OP_MISMATCH_ERROR);
    assertThat(decision.status()).isEqualTo(409);
  }

  @Test
  @DisplayName("payload OP가 current OP와 같으면 allowed decision을 반환한다")
  void payload_op가_current_op와_같으면_allowed_decision을_반환한다() {
    CurrentOpGuardDecision decision =
        guard.requireCurrentOp(
            OperationalPeriodFixtures.INCIDENT_ID, OperationalPeriodFixtures.CURRENT_OP_ID);

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.currentOpId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
  }
}
