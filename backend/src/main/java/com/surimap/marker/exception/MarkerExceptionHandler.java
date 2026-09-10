package com.surimap.marker.exception;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MarkerExceptionHandler {

  @ExceptionHandler(MarkerApiException.class)
  ResponseEntity<Map<String, String>> handleMarkerApiException(MarkerApiException exception) {
    return ResponseEntity.status(exception.getStatus()).body(Map.of("error", exception.getError()));
  }
}
