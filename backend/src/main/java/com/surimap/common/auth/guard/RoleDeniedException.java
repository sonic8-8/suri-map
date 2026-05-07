package com.surimap.common.auth.guard;

import org.springframework.http.HttpStatus;

/** Thrown when the authenticated account does not have the required role. */
public class RoleDeniedException extends GuardException {

  public RoleDeniedException() {
    super("role_denied", HttpStatus.FORBIDDEN);
  }
}
