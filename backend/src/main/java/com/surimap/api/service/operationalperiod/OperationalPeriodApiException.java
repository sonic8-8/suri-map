package com.surimap.api.service.operationalperiod;

import org.springframework.http.HttpStatus;

public class OperationalPeriodApiException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus status;

  private OperationalPeriodApiException(String errorCode, HttpStatus status) {
    super(errorCode);
    this.errorCode = errorCode;
    this.status = status;
  }

  public static OperationalPeriodApiException writeConflict() {
    return new OperationalPeriodApiException("write_conflict", HttpStatus.CONFLICT);
  }

  public static OperationalPeriodApiException idempotencyMismatch() {
    return new OperationalPeriodApiException("idempotency_mismatch", HttpStatus.CONFLICT);
  }

  public String errorCode() {
    return errorCode;
  }

  public HttpStatus status() {
    return status;
  }
}
