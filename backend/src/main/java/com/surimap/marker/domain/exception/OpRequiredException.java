package com.surimap.marker.domain.exception;

/**
 * 사건에 current OP가 없을 때 쓰는 예외다.
 *
 * <p>API 응답에서는 409 op_required로 매핑한다.
 */
public class OpRequiredException extends RuntimeException {

  private final String errorCode;

  /**
   * OpRequiredException을 생성한다.
   *
   * @param message 실패 사유 메시지
   */
  public OpRequiredException(String message) {
    super(message);
    this.errorCode = "op_required";
  }

  /**
   * API error code를 반환한다.
   *
   * @return op_required
   */
  public String errorCode() {
    return errorCode;
  }
}
