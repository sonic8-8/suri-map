package com.surimap.marker.domain.exception;

/**
 * 사건에 current OP가 없을 때 쓰는 예외다.
 *
 * <p>API 응답에서는 409 op_required로 매핑한다.
 */
public class OpRequiredException extends RuntimeException {

  private final String errorCode = "op_required";

  public OpRequiredException() {
    super("op_required");
  }

  public OpRequiredException(String message) {
    super(message);
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String errorCode() {
    return errorCode;
  }
}
