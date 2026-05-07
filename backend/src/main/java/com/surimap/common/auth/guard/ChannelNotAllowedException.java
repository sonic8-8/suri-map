package com.surimap.common.auth.guard;

import org.springframework.http.HttpStatus;

/** Thrown when the request channel is not in the allowed set for an endpoint. */
public class ChannelNotAllowedException extends GuardException {

  public ChannelNotAllowedException() {
    super("channel_not_allowed", HttpStatus.FORBIDDEN);
  }
}
