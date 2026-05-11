package com.surimap.api.controller.operationalperiod;

import com.surimap.api.service.operationalperiod.OperationalPeriodApiException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OperationalPeriodController.class)
public class OperationalPeriodExceptionHandler {

  @ExceptionHandler(OperationalPeriodApiException.class)
  ResponseEntity<Map<String, String>> handleOperationalPeriodApiException(
      OperationalPeriodApiException ex) {
    return ResponseEntity.status(ex.status()).body(Map.of("error", ex.errorCode()));
  }
}
