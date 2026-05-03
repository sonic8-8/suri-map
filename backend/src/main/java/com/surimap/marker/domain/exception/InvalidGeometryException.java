package com.surimap.marker.domain.exception;

/**
 * geometry 검증 실패 시 던지는 예외.
 * HTTP 400, error code: "invalid_geometry"
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

    public String getDetail() {
        return detail;
    }
}
