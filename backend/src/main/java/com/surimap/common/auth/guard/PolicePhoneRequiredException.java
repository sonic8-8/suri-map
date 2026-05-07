package com.surimap.common.auth.guard;

import org.springframework.http.HttpStatus;

/** Thrown when an APP-channel endpoint requires a police phone but none is present. */
public class PolicePhoneRequiredException extends GuardException {

  public PolicePhoneRequiredException() {
    super("police_phone_required", HttpStatus.BAD_REQUEST);
  }
}
