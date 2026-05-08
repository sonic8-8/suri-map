package com.surimap.incident.lifecycle;

import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/** {@link RequireOpenIncident}가 붙은 method의 incidentId 인자로 lifecycle guard를 실행한다. */
@Aspect
@Component
public class RequireOpenIncidentAspect {

  private final IncidentLifecycleGuard incidentLifecycleGuard;

  public RequireOpenIncidentAspect(IncidentLifecycleGuard incidentLifecycleGuard) {
    this.incidentLifecycleGuard = incidentLifecycleGuard;
  }

  @Around("@annotation(com.surimap.incident.lifecycle.RequireOpenIncident)")
  public Object guard(ProceedingJoinPoint pjp) throws Throwable {
    MethodSignature signature = (MethodSignature) pjp.getSignature();
    UUID incidentId = extractIncidentId(signature.getParameterNames(), pjp.getArgs());
    incidentLifecycleGuard.requireOpen(incidentId);
    return pjp.proceed();
  }

  private UUID extractIncidentId(String[] parameterNames, Object[] args) {
    for (int i = 0; i < parameterNames.length; i++) {
      if ("incidentId".equals(parameterNames[i])) {
        return (UUID) args[i];
      }
    }
    throw new IllegalStateException(
        "@RequireOpenIncident: parameter 'incidentId' not found in method signature");
  }
}
