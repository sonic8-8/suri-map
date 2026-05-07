package com.surimap.marker.domain.exception;

/**
 * 요청 opId가 서버 current OP와 다를 때 쓰는 예외다.
 *
 * <p>API 응답에서는 409 op_mismatch로 매핑한다.
 */
public class OpMismatchException extends RuntimeException {

  private final String errorCode;

  /**
   * OpMismatchException을 생성한다.
   *
   * @param message 실패 사유 메시지
   */
  public OpMismatchException(String message) {
    super(message);
    this.errorCode = "op_mismatch";
  }

  /**
   * API error code를 반환한다.
   *
   * @return op_mismatch
   */
  public String errorCode() {
    return errorCode;
  }
}
