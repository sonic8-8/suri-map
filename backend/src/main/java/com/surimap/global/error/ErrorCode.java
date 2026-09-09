package com.surimap.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
  WRITE_CONFLICT(HttpStatus.CONFLICT, "write_conflict"),
  INVALID_GEOMETRY(HttpStatus.BAD_REQUEST, "invalid_geometry");

  private final HttpStatus status;
  private final String error;

  ErrorCode(HttpStatus status, String error) {
    this.status = status;
    this.error = error;
  }
}
