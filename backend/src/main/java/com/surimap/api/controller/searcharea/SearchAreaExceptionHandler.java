package com.surimap.api.controller.searcharea;

import com.surimap.api.service.searcharea.SearchAreaApiException;
import com.surimap.maparea.geometry.exception.InvalidGeometryException;
import com.surimap.maparea.geometry.exception.OverallSearchAreaRequiredException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SearchAreaController.class)
public class SearchAreaExceptionHandler {

  @ExceptionHandler(SearchAreaApiException.class)
  ResponseEntity<Map<String, String>> handleSearchAreaApiException(SearchAreaApiException ex) {
    return ResponseEntity.status(ex.status()).body(Map.of("error", ex.errorCode()));
  }

  @ExceptionHandler(InvalidGeometryException.class)
  ResponseEntity<Map<String, String>> handleInvalidGeometry(InvalidGeometryException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.errorCode()));
  }

  @ExceptionHandler(OverallSearchAreaRequiredException.class)
  ResponseEntity<Map<String, String>> handleOverallRequired(OverallSearchAreaRequiredException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.errorCode()));
  }
}
