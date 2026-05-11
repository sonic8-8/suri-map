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
    return ResponseEntity.status(ex.getHttpStatus())
        .body(new OutboxRequeueErrorResponse(ex.getErrorCode(), null, false, null, null));
  }
}
