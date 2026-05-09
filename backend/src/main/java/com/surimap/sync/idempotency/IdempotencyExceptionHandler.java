package com.surimap.sync.idempotency;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdempotencyExceptionHandler {

  @ExceptionHandler(IdempotencyMismatchException.class)
  ResponseEntity<Map<String, String>> handleMismatch(IdempotencyMismatchException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.errorCode()));
  }

  @ExceptionHandler(WriteConflictException.class)
  ResponseEntity<Map<String, String>> handleWriteConflict(WriteConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.errorCode()));
  }
}
