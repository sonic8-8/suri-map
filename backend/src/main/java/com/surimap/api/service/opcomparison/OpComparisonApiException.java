package com.surimap.api.service.opcomparison;

import org.springframework.http.HttpStatus;

public class OpComparisonApiException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus status;

  private OpComparisonApiException(String errorCode, HttpStatus status) {
    super(errorCode);
    this.errorCode = errorCode;
    this.status = status;
  }

  public static OpComparisonApiException writeConflict() {
    return new OpComparisonApiException("write_conflict", HttpStatus.CONFLICT);
  }

  public static OpComparisonApiException idempotencyMismatch() {
    return new OpComparisonApiException("idempotency_mismatch", HttpStatus.CONFLICT);
  }

  public static OpComparisonApiException invalidComparison() {
    return new OpComparisonApiException(
        "invalid_operational_period_comparison", HttpStatus.BAD_REQUEST);
  }

  public String errorCode() {
    return errorCode;
  }

  public HttpStatus status() {
    return status;
  }
}
