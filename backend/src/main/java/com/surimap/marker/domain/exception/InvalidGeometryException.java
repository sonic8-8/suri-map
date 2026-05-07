package com.surimap.marker.domain.exception;

/**
 * S5 marker.location 검증 실패를 표현하는 예외다.
 *
 * <p>API 응답에서는 400 invalid_geometry로 매핑한다.
 */
public class InvalidGeometryException extends RuntimeException {

  private final String errorCode = "invalid_geometry";
  private final String detail;

  public InvalidGeometryException(String detail) {
    super("invalid_geometry: " + detail);
    this.detail = detail;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String errorCode() {
    return errorCode;
  }

  public String getDetail() {
    return detail;
  }

  public String detail() {
    return detail;
  }

  public static InvalidGeometryException invalidGeometry(String message) {
    return new InvalidGeometryException(message);
  }
}
