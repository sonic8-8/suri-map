package com.surimap.api.controller.sse;

import com.surimap.api.service.sse.ServerSentEventSubscriptionService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.global.sse.ServerSentEventStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
public class ServerSentEventController {

  private final ServerSentEventSubscriptionService streamService;

  public ServerSentEventController(ServerSentEventSubscriptionService streamService) {
    this.streamService = streamService;
  }

  @GetMapping(value = "/api/incidents/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Object subscribeToAccountEvents(HttpServletRequest request, HttpServletResponse response) {
    var authentication = currentWebAuthenticationOrNull();
    if (authentication == null) {
      return jsonError(403, "channel_not_allowed");
    }
    return streamResponse(
        streamService.openAccountStream(UUID.fromString(authentication.getAccountId())),
        request,
        response);
  }

  @GetMapping(
      value = "/api/incidents/{incidentId}/events",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RequireIncidentAccess
  public Object subscribeToIncidentEvents(
      @PathVariable UUID incidentId,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (currentWebAuthenticationOrNull() == null) {
      return jsonError(403, "channel_not_allowed");
    }

    return streamResponse(
        streamService.openIncidentStream(incidentId, lastEventId), request, response);
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

  private DeferredResult<Void> streamResponse(
      DeferredResult<Void> stream, HttpServletRequest request, HttpServletResponse response) {
    request.setAttribute(ServerSentEventStream.REQUEST_ATTRIBUTE, stream);
    response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
    return stream;
  }
}
