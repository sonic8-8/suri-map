package com.surimap.maparea.geometry.exception;

/**
 * active map_boundary가 필요한 요청에서 boundary가 없을 때 사용하는 예외다.
 *
 * <p>API 응답에서는 409 map_boundary_required로 매핑한다.</p>
 */
public class MapBoundaryRequiredException extends RuntimeException {

    // TODO 공통 예외처리 핸들러 추가 시, 상속 및 예외처리 구조 변경 필요
    private final String errorCode;

    private MapBoundaryRequiredException(String message) {
        super(message);
        this.errorCode = "map_boundary_required";
    }

    public static MapBoundaryRequiredException mapBoundaryRequired(String message) {
        return new MapBoundaryRequiredException(message);
    }

    public String errorCode() {
        return errorCode;
    }
}
