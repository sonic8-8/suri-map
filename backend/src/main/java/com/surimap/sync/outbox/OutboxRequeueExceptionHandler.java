package com.surimap.sync.outbox;

import com.surimap.common.auth.guard.GuardException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OutboxRequeueController.class)
class OutboxRequeueExceptionHandler {

  @ExceptionHandler(OutboxRequeueApiException.class)
  ResponseEntity<OutboxRequeueErrorResponse> handleOutboxRequeueApiException(
      OutboxRequeueApiException ex) {
    return ResponseEntity.status(ex.getStatus()).body(ex.getResponse());
  }

  @ExceptionHandler(GuardException.class)
  ResponseEntity<OutboxRequeueErrorResponse> handleGuardException(GuardException ex) {
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

    return ResponseEntity.status(mappedStatus)
        .body(new OutboxRequeueErrorResponse(mappedError, null, false, null, null));
  }
}
