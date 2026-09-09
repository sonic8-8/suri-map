package com.surimap.marker.exception;

import com.surimap.marker.domain.exception.OpMismatchException;
import com.surimap.marker.domain.exception.OpRequiredException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MarkerExceptionHandler {

  @ExceptionHandler(MarkerApiException.class)
  ResponseEntity<Map<String, String>> handleMarkerApiException(MarkerApiException exception) {
    return ResponseEntity.status(exception.getStatus()).body(Map.of("error", exception.getError()));
  }

  @ExceptionHandler(OpRequiredException.class)
  ResponseEntity<Map<String, String>> handleOpRequired(OpRequiredException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.errorCode()));
  }

  @ExceptionHandler(OpMismatchException.class)
  ResponseEntity<Map<String, String>> handleOpMismatch(OpMismatchException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.errorCode()));
  }
}
