package com.surimap.operationalperiod.testdouble;

/**
 * @RequireCurrentOp mock guard 실패.
 */
public final class CurrentOpGuardException extends RuntimeException {

  private final String errorCode;
  private final int httpStatus;

  public CurrentOpGuardException(String errorCode) {
    super(errorCode);
    this.errorCode = errorCode;
    this.httpStatus = 409;
  }

  public String errorCode() {
    return errorCode;
  }

  public int httpStatus() {
    return httpStatus;
  }
}
