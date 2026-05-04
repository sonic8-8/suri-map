package com.surimap.maparea.geometry.exception;

/**
 * S2 도형 검증 실패를 표현하는 예외다.
 *
 * <p>API 응답에서는 400 invalid_geometry로 매핑한다.</p>
 */
public class InvalidGeometryException extends RuntimeException {

    // TODO 공통 예외처리 핸들러 추가 시, 상속 및 예외처리 구조 변경 필요
    private final String errorCode;

    /**
     * InvalidGeometryException을 생성한다.
     *
     * @param message 예외 메시지
     */
    private InvalidGeometryException(String message) {
        super(message);
        this.errorCode = "invalid_geometry";
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

    /**
     * API error code를 반환한다.
     *
     * @return invalid_geometry
     */
    public String errorCode() {
        return errorCode;
    }
}
