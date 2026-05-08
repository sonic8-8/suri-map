package com.surimap.common.auth.guard;

import org.springframework.http.HttpStatus;

public class DeviceNotRegisteredException extends GuardException {

  public DeviceNotRegisteredException() {
    super("device_not_registered", HttpStatus.FORBIDDEN);
  }
}

