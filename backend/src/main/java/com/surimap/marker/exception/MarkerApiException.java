package com.surimap.marker.exception;

import org.springframework.http.HttpStatus;

public class MarkerApiException extends RuntimeException {

  private final String error;
  private final HttpStatus status;

  public MarkerApiException(String error, HttpStatus status) {
    super(error);
    this.error = error;
    this.status = status;
  }

  public String getError() {
    return error;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
