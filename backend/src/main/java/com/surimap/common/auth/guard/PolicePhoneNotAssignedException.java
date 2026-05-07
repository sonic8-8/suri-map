package com.surimap.common.auth.guard;

import org.springframework.http.HttpStatus;

/** Thrown when the police phone is registered but not assigned to an active incident. */
public class PolicePhoneNotAssignedException extends GuardException {

  public PolicePhoneNotAssignedException() {
    super("police_phone_not_assigned", HttpStatus.FORBIDDEN);
  }
}
