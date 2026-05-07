package com.surimap.path;

public class SearchPathApiException extends RuntimeException {
  private final String errorCode;

  public SearchPathApiException(String errorCode) {
    super(errorCode);
    this.errorCode = errorCode;
  }

  public String errorCode() {
    return errorCode;
  }
}
