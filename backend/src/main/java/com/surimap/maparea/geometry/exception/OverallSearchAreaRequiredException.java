package com.surimap.maparea.geometry.exception;

/**
 * active overall_search_area가 필요한 요청에서 area가 없을 때 사용하는 예외다.
 *
 * <p>API 응답에서는 409 overall_search_area_required로 매핑한다.
 */
public class OverallSearchAreaRequiredException extends RuntimeException {

  // TODO 공통 예외처리 핸들러 추가 시, 상속 및 예외처리 구조 변경 필요
  private final String errorCode;

  private OverallSearchAreaRequiredException(String message) {
    super(message);
    this.errorCode = "overall_search_area_required";
  }

  public static OverallSearchAreaRequiredException overallSearchAreaRequired(String message) {
    return new OverallSearchAreaRequiredException(message);
  }

  public String errorCode() {
    return errorCode;
  }
}
