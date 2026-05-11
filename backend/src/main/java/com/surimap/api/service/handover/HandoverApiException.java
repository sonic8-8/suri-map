package com.surimap.api.service.handover;

import org.springframework.http.HttpStatus;

public class HandoverApiException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus status;

  private HandoverApiException(String errorCode, HttpStatus status) {
    super(errorCode);
    this.errorCode = errorCode;
    this.status = status;
  }

  public static HandoverApiException writeConflict() {
    return new HandoverApiException("write_conflict", HttpStatus.CONFLICT);
  }

  public static HandoverApiException idempotencyMismatch() {
    return new HandoverApiException("idempotency_mismatch", HttpStatus.CONFLICT);
  }

  public static HandoverApiException opRequired() {
    return new HandoverApiException("op_required", HttpStatus.CONFLICT);
  }

  public static HandoverApiException opMismatch() {
    return new HandoverApiException("op_mismatch", HttpStatus.CONFLICT);
  }

  public String errorCode() {
    return errorCode;
  }

  public HttpStatus status() {
    return status;
  }
}
