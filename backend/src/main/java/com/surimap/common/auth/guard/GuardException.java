package com.surimap.common.auth.guard;

import org.springframework.http.HttpStatus;

/** Base exception for all guard violations. */
public abstract class GuardException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus httpStatus;

  protected GuardException(String errorCode, HttpStatus httpStatus) {
    super(errorCode);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
