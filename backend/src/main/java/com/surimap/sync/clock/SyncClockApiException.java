package com.surimap.sync.clock;

import org.springframework.http.HttpStatus;

public class SyncClockApiException extends RuntimeException {

  private final HttpStatus status;
  private final String error;

  public SyncClockApiException(HttpStatus status, String error) {
    super(error);
    this.status = status;
    this.error = error;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getError() {
    return error;
  }
}
