package com.surimap.global.error;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getError());
    this.errorCode = errorCode;
  }

  public BusinessException(ErrorCode errorCode, String detail) {
    super(errorCode.getError() + ": " + detail);
    this.errorCode = errorCode;
  }
}
