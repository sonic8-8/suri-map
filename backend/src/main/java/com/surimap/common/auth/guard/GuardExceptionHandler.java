package com.surimap.common.auth.guard;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates guard exceptions into {@code {"error":"<code>"}} JSON responses. */
@RestControllerAdvice
public class GuardExceptionHandler {

  @ExceptionHandler(GuardException.class)
  ResponseEntity<Map<String, String>> handleGuardException(GuardException ex) {
    return ResponseEntity.status(ex.getHttpStatus()).body(Map.of("error", ex.getErrorCode()));
  }
}
