package com.surimap.sync.clock;

import com.surimap.common.auth.guard.GuardException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = SyncClockController.class)
public class SyncClockExceptionHandler {

  @ExceptionHandler(SyncClockApiException.class)
  ResponseEntity<Map<String, String>> handleSyncClockApiException(SyncClockApiException ex) {
    return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getError()));
  }

  @ExceptionHandler(GuardException.class)
  ResponseEntity<Map<String, String>> handleGuardException(GuardException ex) {
    return ResponseEntity.status(ex.getHttpStatus()).body(Map.of("error", ex.getErrorCode()));
  }
}
