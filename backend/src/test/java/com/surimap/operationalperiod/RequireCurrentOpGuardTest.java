package com.surimap.operationalperiod;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.guard.OpMismatchException;
import com.surimap.operationalperiod.guard.OpRequiredException;
import com.surimap.operationalperiod.guard.RequireCurrentOpAspect;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L3-T05B @RequireCurrentOp guard aspect 단위 테스트. */
@DisplayName("L3-T05B @RequireCurrentOp guard aspect")
class RequireCurrentOpGuardTest {

  private final OperationalPeriodQuery opQuery = mock(OperationalPeriodQuery.class);
  private final RequireCurrentOpAspect aspect = new RequireCurrentOpAspect(opQuery);

  @Test
  @DisplayName("current OP가 없으면 OpRequiredException(op_required 409)이 발생한다")
  void current_op_없으면_OpRequiredException_발생() throws Throwable {
    when(opQuery.current(OperationalPeriodFixtures.INCIDENT_ID)).thenReturn(Optional.empty());

    ProceedingJoinPoint pjp =
        mockJoinPoint(
            OperationalPeriodFixtures.INCIDENT_ID, OperationalPeriodFixtures.CURRENT_OP_ID);

    assertThatThrownBy(() -> aspect.guard(pjp))
        .isInstanceOf(OpRequiredException.class)
        .hasMessageContaining("op_required");
  }

  @Test
  @DisplayName("요청 opId가 current OP와 다르면 OpMismatchException(op_mismatch 409)이 발생한다")
  void opId_불일치하면_OpMismatchException_발생() throws Throwable {
    when(opQuery.current(OperationalPeriodFixtures.INCIDENT_ID))
        .thenReturn(Optional.of(OperationalPeriodFixtures.currentOpResult()));

    ProceedingJoinPoint pjp =
        mockJoinPoint(OperationalPeriodFixtures.INCIDENT_ID, OperationalPeriodFixtures.NEW_OP_ID);

    assertThatThrownBy(() -> aspect.guard(pjp))
        .isInstanceOf(OpMismatchException.class)
        .hasMessageContaining("op_mismatch");
  }

  @Test
  @DisplayName("요청 opId가 current OP와 일치하면 proceed()를 호출한다")
  void opId_일치하면_proceed_호출한다() throws Throwable {
    when(opQuery.current(OperationalPeriodFixtures.INCIDENT_ID))
        .thenReturn(Optional.of(OperationalPeriodFixtures.currentOpResult()));

    ProceedingJoinPoint pjp =
        mockJoinPoint(
            OperationalPeriodFixtures.INCIDENT_ID, OperationalPeriodFixtures.CURRENT_OP_ID);
    when(pjp.proceed()).thenReturn(null);

    aspect.guard(pjp);

    org.mockito.Mockito.verify(pjp).proceed();
  }

  private ProceedingJoinPoint mockJoinPoint(java.util.UUID incidentId, java.util.UUID opId) {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    MethodSignature sig = mock(MethodSignature.class);
    when(pjp.getSignature()).thenReturn(sig);
    when(sig.getParameterNames()).thenReturn(new String[] {"incidentId", "opId"});
    when(pjp.getArgs()).thenReturn(new Object[] {incidentId, opId});
    return pjp;
  }
}
