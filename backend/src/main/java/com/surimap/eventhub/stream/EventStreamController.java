package com.surimap.eventhub.stream;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.SuriMapAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class EventStreamController {

  private final SseStreamService streamService;

  public EventStreamController(SseStreamService streamService) {
    this.streamService = streamService;
  }

  @GetMapping(value = "/api/incidents/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<?> streamAssignedIncidents(HttpServletRequest request) {
    var authentication = currentWebAuthenticationOrNull();
    if (authentication == null) {
      return jsonError(403, "channel_not_allowed");
    }
    return streamResponse(
        streamService.openAccountStream(UUID.fromString(authentication.getAccountId())), request);
  }

  @GetMapping(
      value = "/api/incidents/{incidentId}/events",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RequireIncidentAccess
  public ResponseEntity<?> stream(
      @PathVariable UUID incidentId,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      HttpServletRequest request) {
    if (currentWebAuthenticationOrNull() == null) {
      return jsonError(403, "channel_not_allowed");
    }

    try {
      return streamResponse(streamService.openStream(incidentId, lastEventId), request);
    } catch (GoneRefetchRequiredException e) {
      return jsonError(409, "gone_refetch_required");
    }
  }

  private SuriMapAuthentication currentWebAuthenticationOrNull() {
    if (SecurityContextHolder.getContext().getAuthentication()
            instanceof SuriMapAuthentication authentication
        && authentication.getChannel() == Channel.WEB) {
      return authentication;
    }
    return null;
  }

  private ResponseEntity<Map<String, String>> jsonError(int status, String errorCode) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("error", errorCode));
  }

  private ResponseEntity<SseEmitter> streamResponse(
      SseEmitter emitter, HttpServletRequest request) {
    request.setAttribute(SseStreamEmitter.REQUEST_ATTRIBUTE, emitter);
    return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
  }
}
