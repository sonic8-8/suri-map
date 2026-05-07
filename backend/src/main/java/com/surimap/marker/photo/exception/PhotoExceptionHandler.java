package com.surimap.marker.photo.exception;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PhotoExceptionHandler {

  @ExceptionHandler(PhotoApiException.class)
  ResponseEntity<Map<String, String>> handlePhotoApiException(PhotoApiException exception) {
    return ResponseEntity.status(exception.getStatus()).body(Map.of("error", exception.getError()));
  }
}
