package com.surimap.offlinepackage.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TileExceptionHandler {

  @ExceptionHandler(TileChannelNotAllowedException.class)
  ResponseEntity<Map<String, String>> handleChannelNotAllowed() {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "channel_not_allowed"));
  }

  @ExceptionHandler(TileUnavailableException.class)
  ResponseEntity<Map<String, String>> handleTileUnavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("error", "tile_unavailable"));
  }

  @ExceptionHandler(OfflinePackageApiException.class)
  ResponseEntity<Map<String, String>> handleOfflinePackageApi(
      OfflinePackageApiException exception) {
    return ResponseEntity.status(exception.status()).body(Map.of("error", exception.errorCode()));
  }
}
