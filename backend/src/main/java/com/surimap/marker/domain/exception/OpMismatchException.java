package com.surimap.marker.domain.exception;

import java.util.UUID;

/** 요청 opId와 current OP가 다른 marker write를 409 op_mismatch로 매핑하기 위한 예외다. */
public class OpMismatchException extends RuntimeException {

  private final String errorCode = "op_mismatch";

  public OpMismatchException(UUID requestedOpId, UUID currentOpId) {
    super("op_mismatch: requested=" + requestedOpId + ", current=" + currentOpId);
  }

  public String getErrorCode() {
    return errorCode;
  }
}
