package com.surimap.common.auth.guard;

import org.springframework.http.HttpStatus;

/** Thrown when a MISSING_TEAM account is not yet assigned to an active incident team. */
public class TeamNotAssignedException extends GuardException {

  public TeamNotAssignedException() {
    super("team_not_assigned", HttpStatus.FORBIDDEN);
  }
}
