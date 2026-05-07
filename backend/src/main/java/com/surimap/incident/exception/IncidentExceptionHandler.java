package com.surimap.incident.exception;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.surimap.incident")
public class IncidentExceptionHandler {

  @ExceptionHandler(IncidentApiException.class)
  ResponseEntity<Map<String, String>> handleIncidentApiException(IncidentApiException exception) {
    return ResponseEntity.status(exception.status()).body(Map.of("error", exception.error()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Map<String, String>> handleDataIntegrityViolation() {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "write_conflict"));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, String>> handleValidationFailure() {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "write_conflict"));
  }

  @ExceptionHandler(IncidentImportDependencyException.class)
  ResponseEntity<Void> handleImportDependencyFailure() {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
  }
}
