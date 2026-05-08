package com.surimap.common.auth.guard;

import org.springframework.http.HttpStatus;

public class DeviceNotAssignedException extends GuardException {

  public DeviceNotAssignedException() {
    super("device_not_assigned", HttpStatus.FORBIDDEN);
  }
}

