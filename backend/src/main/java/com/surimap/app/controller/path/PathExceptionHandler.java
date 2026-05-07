package com.surimap.app.controller.path;

import com.surimap.domain.path.exception.SearchPathGuardException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PathExceptionHandler {

  @ExceptionHandler(SearchPathGuardException.class)
  ResponseEntity<Map<String, String>> handleGuard(SearchPathGuardException exception) {
    return ResponseEntity.status(statusOf(exception.errorCode()))
        .body(Map.of("error", exception.errorCode()));
  }

  private HttpStatus statusOf(String code) {
    return switch (code) {
      case "police_phone_required" -> HttpStatus.BAD_REQUEST;
      case "op_required", "op_mismatch", "write_conflict" -> HttpStatus.CONFLICT;
      case "police_phone_not_registered", "police_phone_not_assigned" -> HttpStatus.FORBIDDEN;
      default -> HttpStatus.BAD_REQUEST;
    };
  }
}
