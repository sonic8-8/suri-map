package com.surimap.app.service.searcharea;

public class SearchAreaBoundaryAlertException extends RuntimeException {

  private final String errorCode;

  public SearchAreaBoundaryAlertException(String errorCode) {
    super(errorCode);
    this.errorCode = errorCode;
  }

  public String errorCode() {
    return errorCode;
  }
}
