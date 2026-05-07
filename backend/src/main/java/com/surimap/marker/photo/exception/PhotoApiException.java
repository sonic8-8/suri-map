package com.surimap.marker.photo.exception;

import org.springframework.http.HttpStatus;

public class PhotoApiException extends RuntimeException {

  private final String error;
  private final HttpStatus status;

  public PhotoApiException(String error, HttpStatus status) {
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
