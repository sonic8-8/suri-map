package com.surimap.incident.lifecycle;

import com.surimap.common.auth.guard.GuardException;
import org.springframework.http.HttpStatus;

/** incident lifecycle guard 실패를 API 표준 error body로 변환하기 위한 예외. */
public final class IncidentLifecycleGuardException extends GuardException {

  private IncidentLifecycleGuardException(String error) {
    super(error, HttpStatus.CONFLICT);
  }

  public static IncidentLifecycleGuardException incidentClosed() {
    return new IncidentLifecycleGuardException("incident_closed");
  }

  public static IncidentLifecycleGuardException incidentBootstrapping() {
    return new IncidentLifecycleGuardException("incident_bootstrapping");
  }

  public String error() {
    return getErrorCode();
  }

  public HttpStatus status() {
    return getHttpStatus();
  }
}
