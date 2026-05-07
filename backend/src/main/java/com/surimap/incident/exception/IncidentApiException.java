package com.surimap.incident.exception;

import org.springframework.http.HttpStatus;

public class IncidentApiException extends RuntimeException {

  private final String error;
  private final HttpStatus status;

  public IncidentApiException(String error, HttpStatus status) {
    super(error);
    this.error = error;
    this.status = status;
  }

  public IncidentApiException(String error, HttpStatus status, Throwable cause) {
    super(error, cause);
    this.error = error;
    this.status = status;
  }

  public String error() {
    return error;
  }

  public HttpStatus status() {
    return status;
  }
}
