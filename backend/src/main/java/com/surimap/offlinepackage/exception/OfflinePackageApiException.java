package com.surimap.offlinepackage.exception;

import org.springframework.http.HttpStatus;

public class OfflinePackageApiException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus status;

  public OfflinePackageApiException(String errorCode, HttpStatus status) {
    super(errorCode);
    this.errorCode = errorCode;
    this.status = status;
  }

  public String errorCode() {
    return errorCode;
  }

  public HttpStatus status() {
    return status;
  }
}
