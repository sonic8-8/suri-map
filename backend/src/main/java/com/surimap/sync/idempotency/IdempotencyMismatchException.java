package com.surimap.sync.idempotency;

public class IdempotencyMismatchException extends RuntimeException {

  public IdempotencyMismatchException() {
    super("idempotency_mismatch");
  }

  public String errorCode() {
    return "idempotency_mismatch";
  }
}
