package com.surimap.domain.path.exception;

public class SearchPathGuardException extends RuntimeException {

  private final String errorCode;

  public SearchPathGuardException(String errorCode) {
    super(errorCode);
    this.errorCode = errorCode;
  }

  public String errorCode() {
    return errorCode;
  }
}
