package com.surimap.eventhub.stream;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.SuriMapAuthentication;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventStreamController {

  private final SseStreamService streamService;

  public EventStreamController(SseStreamService streamService) {
    this.streamService = streamService;
  }

  @GetMapping(
      value = "/api/incidents/{incidentId}/events",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RequireIncidentAccess
  public ResponseEntity<?> stream(
      @PathVariable UUID incidentId,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
    if (!(SecurityContextHolder.getContext().getAuthentication()
            instanceof SuriMapAuthentication authentication)
        || authentication.getChannel() != Channel.WEB) {
      return jsonError(403, "channel_not_allowed");
    }

    try {
      return ResponseEntity.ok()
          .contentType(MediaType.TEXT_EVENT_STREAM)
          .body(streamService.openStream(incidentId, lastEventId));
    } catch (GoneRefetchRequiredException e) {
      return jsonError(409, "gone_refetch_required");
    }
  }

  private ResponseEntity<Map<String, String>> jsonError(int status, String errorCode) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("error", errorCode));
  }
}
