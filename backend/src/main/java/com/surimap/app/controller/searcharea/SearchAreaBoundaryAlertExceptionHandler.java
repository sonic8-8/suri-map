package com.surimap.app.controller.searcharea;

import com.surimap.app.service.searcharea.SearchAreaBoundaryAlertException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SearchAreaBoundaryAlertExceptionHandler {

  @ExceptionHandler(SearchAreaBoundaryAlertException.class)
  ResponseEntity<Map<String, String>> handle(SearchAreaBoundaryAlertException exception) {
    return ResponseEntity.status(statusOf(exception.errorCode()))
        .body(Map.of("error", exception.errorCode()));
  }

  private HttpStatus statusOf(String code) {
    return switch (code) {
      case "police_phone_required", "invalid_geometry" -> HttpStatus.BAD_REQUEST;
      case "police_phone_not_registered",
          "police_phone_not_assigned",
          "incident_access_denied",
          "team_not_assigned",
          "channel_not_allowed" -> HttpStatus.FORBIDDEN;
      case "op_required", "op_mismatch", "write_conflict", "idempotency_mismatch" -> HttpStatus.CONFLICT;
      default -> HttpStatus.BAD_REQUEST;
    };
  }
}
