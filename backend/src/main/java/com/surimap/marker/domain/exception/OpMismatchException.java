package com.surimap.marker.domain.exception;

import java.util.UUID;

/**
 * 요청 opId가 서버 current OP와 다를 때 쓰는 예외다.
 *
 * <p>API 응답에서는 409 op_mismatch로 매핑한다.
 */
public class OpMismatchException extends RuntimeException {

  private final String errorCode = "op_mismatch";
  private final UUID requestedOpId;
  private final UUID currentOpId;

  public OpMismatchException(UUID requestedOpId, UUID currentOpId) {
    super("op_mismatch: requested=" + requestedOpId + ", current=" + currentOpId);
    this.requestedOpId = requestedOpId;
    this.currentOpId = currentOpId;
  }

  public OpMismatchException(String message) {
    super(message);
    this.requestedOpId = null;
    this.currentOpId = null;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String errorCode() {
    return errorCode;
  }

  public UUID requestedOpId() {
    return requestedOpId;
  }

  public UUID currentOpId() {
    return currentOpId;
  }
}
