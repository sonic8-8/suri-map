package com.surimap.common.auth.guard;

import org.springframework.http.HttpStatus;

public class DeviceRequiredException extends GuardException {

  public DeviceRequiredException() {
    super("device_required", HttpStatus.BAD_REQUEST);
  }
}
