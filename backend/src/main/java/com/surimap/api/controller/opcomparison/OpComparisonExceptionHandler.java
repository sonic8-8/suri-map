package com.surimap.api.controller.opcomparison;

import com.surimap.api.service.opcomparison.OpComparisonApiException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OpComparisonController.class)
public class OpComparisonExceptionHandler {

  @ExceptionHandler(OpComparisonApiException.class)
  ResponseEntity<Map<String, String>> handleOpComparisonApiException(
      OpComparisonApiException ex) {
    return ResponseEntity.status(ex.status()).body(Map.of("error", ex.errorCode()));
  }
}
