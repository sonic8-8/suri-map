package com.surimap.domain.path.validation;

public class InvalidGpsPathBatchException extends RuntimeException {

  private final String errorCode;

  public InvalidGpsPathBatchException(String detail) {
    super("invalid_geometry: " + detail);
    this.errorCode = "invalid_geometry";
  }

  public String errorCode() {
    return errorCode;
  }
}
