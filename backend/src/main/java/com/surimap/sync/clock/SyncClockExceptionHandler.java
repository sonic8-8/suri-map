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
    String mappedError =
        switch (ex.getErrorCode()) {
          case "police_phone_required" -> "device_required";
          case "police_phone_not_registered" -> "device_not_registered";
          case "police_phone_not_assigned" -> "device_not_assigned";
          default -> ex.getErrorCode();
        };

    HttpStatus mappedStatus =
        switch (mappedError) {
          case "device_required" -> HttpStatus.BAD_REQUEST;
          case "device_not_registered", "device_not_assigned", "channel_not_allowed" ->
              HttpStatus.FORBIDDEN;
          default -> ex.getHttpStatus();
        };

    return ResponseEntity.status(mappedStatus).body(Map.of("error", mappedError));
  }
}

