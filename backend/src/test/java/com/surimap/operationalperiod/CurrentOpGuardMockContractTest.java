package com.surimap.operationalperiod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.operationalperiod.testdouble.CurrentOpGuardException;
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

    assertThatThrownBy(
            () -> guard.requireCurrent(unknownIncidentId, OperationalPeriodFixtures.OP1_ID))
        .isInstanceOfSatisfying(
            CurrentOpGuardException.class,
            error -> {
              assertThat(error.errorCode()).isEqualTo("op_required");
              assertThat(error.httpStatus()).isEqualTo(409);
            });
  }

  @Test
  @DisplayName("payload OP와 서버 current OP가 다르면 409 op_mismatch로 실패한다")
  void payload_op와_current_op가_다르면_op_mismatch로_실패한다() {
    assertThatThrownBy(
            () ->
                guard.requireCurrent(
                    OperationalPeriodFixtures.INCIDENT_ID, OperationalPeriodFixtures.OP2_ID))
        .isInstanceOfSatisfying(
            CurrentOpGuardException.class,
            error -> {
              assertThat(error.errorCode()).isEqualTo("op_mismatch");
              assertThat(error.httpStatus()).isEqualTo(409);
            });
  }

  @Test
  @DisplayName("payload OP가 current OP와 같으면 current row를 반환한다")
  void payload_op가_current_op와_같으면_current_row를_반환한다() {
    OperationalPeriodRow row =
        guard.requireCurrent(
            OperationalPeriodFixtures.INCIDENT_ID, OperationalPeriodFixtures.OP1_ID);

    assertThat(row.opId()).isEqualTo(OperationalPeriodFixtures.OP1_ID);
    assertThat(row.status()).isEqualTo("ACTIVE");
  }
}
