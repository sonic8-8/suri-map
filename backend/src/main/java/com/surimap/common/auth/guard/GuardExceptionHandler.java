package com.surimap.common.auth.guard;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates guard exceptions into {@code {"error":"<code>"}} JSON responses. */
@RestControllerAdvice
public class GuardExceptionHandler {

  @ExceptionHandler(GuardException.class)
  ResponseEntity<byte[]> handleGuardException(GuardException ex) {
    byte[] body = ("{\"error\":\"" + ex.getErrorCode() + "\"}").getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.status(ex.getHttpStatus())
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(body);
  }
}
