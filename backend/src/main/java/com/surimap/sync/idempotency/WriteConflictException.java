package com.surimap.sync.idempotency;

public class WriteConflictException extends RuntimeException {

  public WriteConflictException() {
    super("write_conflict");
  }

  public String errorCode() {
    return "write_conflict";
  }
}
