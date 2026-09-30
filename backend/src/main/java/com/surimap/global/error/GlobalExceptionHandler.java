package com.surimap.global.error;

import com.surimap.global.sse.ServerSentEventRefetchRequiredException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ServerSentEventRefetchRequiredException.class)
  public ResponseEntity<Map<String, String>> handleServerSentEventRefetchRequired() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("error", "gone_refetch_required"));
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<Map<String, String>> handleBusinessException(BusinessException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    return ResponseEntity.status(errorCode.getStatus()).body(Map.of("error", errorCode.getError()));
  }
}
