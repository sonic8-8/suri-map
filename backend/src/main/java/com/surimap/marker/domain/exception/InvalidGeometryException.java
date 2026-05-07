package com.surimap.marker.domain.exception;

/**
 * S5 marker.location 검증 실패를 표현하는 예외다.
 *
 * <p>API 응답에서는 400 invalid_geometry로 매핑한다.
 */
public class InvalidGeometryException extends RuntimeException {

  private final String errorCode;

  /**
   * InvalidGeometryException을 생성한다.
   *
   * @param message 실패 사유 메시지
   */
  public InvalidGeometryException(String message) {
    super(message);
    this.errorCode = "invalid_geometry";
  }

  /**
   * API error code를 반환한다.
   *
   * @return invalid_geometry
   */
  public String errorCode() {
    return errorCode;
  }

  /**
   * invalid_geometry 예외를 생성한다.
   *
   * @param message 실패 사유 메시지
   * @return InvalidGeometryException
   */
  public static InvalidGeometryException invalidGeometry(String message) {
    return new InvalidGeometryException(message);
  }
}
