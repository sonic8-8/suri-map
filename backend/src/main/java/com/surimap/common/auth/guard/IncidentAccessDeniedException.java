package com.surimap.common.auth.guard;

import org.springframework.http.HttpStatus;

/** Thrown when the account is not assigned to the incident. */
public class IncidentAccessDeniedException extends GuardException {

  public IncidentAccessDeniedException() {
    super("incident_access_denied", HttpStatus.FORBIDDEN);
  }
}
