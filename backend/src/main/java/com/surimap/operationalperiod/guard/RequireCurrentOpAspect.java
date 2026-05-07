package com.surimap.operationalperiod.guard;

import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import java.util.Optional;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * @RequireCurrentOp 가 붙은 메서드에 대해 current OP 존재 여부와 opId 일치 여부를 검사한다.
 *
 * <p>적용 메서드 파라미터에 'incidentId'(UUID)와 'opId'(UUID)가 있어야 한다.
 */
@Aspect
@Component
public class RequireCurrentOpAspect {

  private final OperationalPeriodQuery opQuery;

  public RequireCurrentOpAspect(OperationalPeriodQuery opQuery) {
    this.opQuery = opQuery;
  }

  @Around("@annotation(com.surimap.operationalperiod.guard.RequireCurrentOp)")
  public Object guard(ProceedingJoinPoint pjp) throws Throwable {
    MethodSignature sig = (MethodSignature) pjp.getSignature();
    String[] paramNames = sig.getParameterNames();
    Object[] args = pjp.getArgs();

    UUID incidentId = extractParam(paramNames, args, "incidentId");
    UUID requestedOpId = extractParam(paramNames, args, "opId");

    Optional<CurrentOpResult> currentOp = opQuery.current(incidentId);
    if (currentOp.isEmpty()) {
      throw new OpRequiredException();
    }

    if (!currentOp.get().opId().equals(requestedOpId)) {
      throw new OpMismatchException();
    }

    return pjp.proceed();
  }

  @SuppressWarnings("unchecked")
  private <T> T extractParam(String[] paramNames, Object[] args, String name) {
    for (int i = 0; i < paramNames.length; i++) {
      if (name.equals(paramNames[i])) {
        return (T) args[i];
      }
    }
    throw new IllegalStateException(
        "@RequireCurrentOp: parameter '" + name + "' not found in method signature");
  }
}
