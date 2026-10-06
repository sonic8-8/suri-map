package com.surimap.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
  INVALID_SEARCH_PATH_QUERY(HttpStatus.BAD_REQUEST, "invalid_search_path_query"),
  SEARCH_PATH_QUERY_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE, "search_path_query_not_ready"),
  SEARCH_PATH_SEGMENT_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY, "search_path_segment_too_large"),
  WRITE_CONFLICT(HttpStatus.CONFLICT, "write_conflict"),
  PHOTO_LIMIT_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "photo_limit_exceeded"),
  INCIDENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "incident_access_denied"),
  INCIDENT_CLOSED(HttpStatus.CONFLICT, "incident_closed"),
  CHANNEL_NOT_ALLOWED(HttpStatus.FORBIDDEN, "channel_not_allowed"),
  TEAM_NOT_ASSIGNED(HttpStatus.FORBIDDEN, "team_not_assigned"),
  OP_REQUIRED(HttpStatus.CONFLICT, "op_required"),
  OP_MISMATCH(HttpStatus.CONFLICT, "op_mismatch"),
  POLICE_PHONE_REQUIRED(HttpStatus.BAD_REQUEST, "police_phone_required"),
  POLICE_PHONE_NOT_REGISTERED(HttpStatus.FORBIDDEN, "police_phone_not_registered"),
  POLICE_PHONE_NOT_ASSIGNED(HttpStatus.FORBIDDEN, "police_phone_not_assigned"),
  INVALID_PHOTO_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "invalid_photo_content_type"),
  INVALID_GEOMETRY(HttpStatus.BAD_REQUEST, "invalid_geometry"),
  INVALID_MARKER_FILTER(HttpStatus.BAD_REQUEST, "invalid_marker_filter");

  private final HttpStatus status;
  private final String error;

  ErrorCode(HttpStatus status, String error) {
    this.status = status;
    this.error = error;
  }
}
