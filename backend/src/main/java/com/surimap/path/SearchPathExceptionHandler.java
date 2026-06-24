package com.surimap.path;

import com.surimap.path.validation.InvalidGpsPathBatchException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SearchPathExceptionHandler {

  @ExceptionHandler(SearchPathApiException.class)
  ResponseEntity<Map<String, String>> handleApi(SearchPathApiException exception) {
    return ResponseEntity.status(status(exception.errorCode()))
        .body(Map.of("error", exception.errorCode()));
  }

  @ExceptionHandler(InvalidGpsPathBatchException.class)
  ResponseEntity<Map<String, String>> handleInvalidBatch(InvalidGpsPathBatchException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", exception.errorCode()));
  }

  private HttpStatus status(String code) {
    return switch (code) {
      case "police_phone_required" -> HttpStatus.BAD_REQUEST;
      case "channel_not_allowed", "incident_access_denied", "police_phone_not_registered" ->
          HttpStatus.FORBIDDEN;
      default -> HttpStatus.CONFLICT;
    };
  }
}
