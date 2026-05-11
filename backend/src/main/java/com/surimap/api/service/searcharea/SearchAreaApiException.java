package com.surimap.api.service.searcharea;

import org.springframework.http.HttpStatus;

public class SearchAreaApiException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus status;

  private SearchAreaApiException(String errorCode, HttpStatus status) {
    super(errorCode);
    this.errorCode = errorCode;
    this.status = status;
  }

  public static SearchAreaApiException invalidGeometry() {
    return new SearchAreaApiException("invalid_geometry", HttpStatus.BAD_REQUEST);
  }

  public static SearchAreaApiException overallSearchAreaRequired() {
    return new SearchAreaApiException("overall_search_area_required", HttpStatus.CONFLICT);
  }

  public static SearchAreaApiException areaStateConflict() {
    return new SearchAreaApiException("area_state_conflict", HttpStatus.CONFLICT);
  }

  public static SearchAreaApiException writeConflict() {
    return new SearchAreaApiException("write_conflict", HttpStatus.CONFLICT);
  }

  public static SearchAreaApiException idempotencyMismatch() {
    return new SearchAreaApiException("idempotency_mismatch", HttpStatus.CONFLICT);
  }

  public String errorCode() {
    return errorCode;
  }

  public HttpStatus status() {
    return status;
  }
}
