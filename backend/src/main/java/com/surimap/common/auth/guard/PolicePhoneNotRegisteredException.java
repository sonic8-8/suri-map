package com.surimap.common.auth.guard;

import org.springframework.http.HttpStatus;

/** Thrown when the police phone in the session is not registered in the system. */
public class PolicePhoneNotRegisteredException extends GuardException {

  public PolicePhoneNotRegisteredException() {
    super("police_phone_not_registered", HttpStatus.FORBIDDEN);
  }
}
