package com.surimap.marker.domain.exception;

/** 현재 OP가 없는 marker write를 409 op_required로 매핑하기 위한 예외다. */
public class OpRequiredException extends RuntimeException {

  private final String errorCode = "op_required";

  public OpRequiredException() {
    super("op_required");
  }

  public String getErrorCode() {
    return errorCode;
  }
}
