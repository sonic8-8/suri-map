package com.surimap.eventhub.stream;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class EventStreamExceptionHandler {

  @ExceptionHandler(GoneRefetchRequiredException.class)
  ResponseEntity<Map<String, String>> handleGoneRefetchRequired() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "gone_refetch_required"));
  }
}
