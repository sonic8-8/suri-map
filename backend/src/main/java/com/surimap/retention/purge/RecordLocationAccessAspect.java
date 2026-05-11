package com.surimap.retention.purge;

import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.auth.guard.ChannelNotAllowedException;
import java.time.Clock;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** {@link RecordLocationAccess}가 붙은 method의 incidentId 조회를 감사 기록으로 남긴다. */
@Aspect
@Component
public class RecordLocationAccessAspect {

  private final LocationAccessRecorder recorder;
  private final Clock clock;

  public RecordLocationAccessAspect(LocationAccessRecorder recorder, Clock clock) {
    this.recorder = recorder;
    this.clock = clock;
  }

  @Around("@annotation(recordLocationAccess)")
  public Object record(ProceedingJoinPoint pjp, RecordLocationAccess recordLocationAccess)
      throws Throwable {
    Object result = pjp.proceed();

    MethodSignature signature = (MethodSignature) pjp.getSignature();
    UUID incidentId = extractIncidentId(signature.getParameterNames(), pjp.getArgs());
    SuriMapAuthentication auth = currentAuthentication();

    recorder.record(
        auth.getAccountId(),
        incidentId,
        parseNullableUuid(auth.getPolicePhoneId(), "policePhoneId"),
        auth.getChannel().name(),
        recordLocationAccess.accessPurpose(),
        clock.instant());

    return result;
  }

  private SuriMapAuthentication currentAuthentication() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof SuriMapAuthentication suriMapAuthentication) {
      return suriMapAuthentication;
    }
    throw new ChannelNotAllowedException();
  }

  private UUID extractIncidentId(String[] parameterNames, Object[] args) {
    for (int i = 0; i < parameterNames.length; i++) {
      if ("incidentId".equals(parameterNames[i])) {
        return (UUID) args[i];
      }
    }
    throw new IllegalStateException(
        "@RecordLocationAccess: parameter 'incidentId' not found in method signature");
  }

  private UUID parseNullableUuid(String value, String fieldName) {
    if (value == null) {
      return null;
    }
    return parseUuid(value, fieldName);
  }

  private UUID parseUuid(String value, String fieldName) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "@RecordLocationAccess: " + fieldName + " must be a UUID", e);
    }
  }
}
