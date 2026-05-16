package com.surimap.incident.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.incident.controller.request.Mock112WebhookEventRequest;
import com.surimap.incident.controller.response.Mock112WebhookEventResponse;
import com.surimap.incident.exception.IncidentApiException;
import com.surimap.incident.service.Mock112WebhookCommand;
import com.surimap.incident.service.Mock112WebhookService;
import com.surimap.incident.service.Mock112WebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Mock112WebhookController {

  private final ObjectMapper objectMapper;
  private final Mock112WebhookSignatureVerifier signatureVerifier;
  private final Mock112WebhookService webhookService;

  public Mock112WebhookController(
      ObjectMapper objectMapper,
      Mock112WebhookSignatureVerifier signatureVerifier,
      Mock112WebhookService webhookService) {
    this.objectMapper = objectMapper;
    this.signatureVerifier = signatureVerifier;
    this.webhookService = webhookService;
  }

  @PostMapping("/api/internal/mock-112/events")
  public ResponseEntity<Mock112WebhookEventResponse> receive(
      @RequestHeader("X-Client-Channel") String clientChannel,
      @RequestHeader(value = "X-Mock112-Signature", required = false) String signature,
      @RequestBody String rawBody) {
    if (!"INTERNAL".equals(clientChannel)) {
      throw new IncidentApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
    signatureVerifier.verify(rawBody, signature);
    Mock112WebhookEventRequest request = parseRequest(rawBody);
    var result =
        webhookService.handle(
            new Mock112WebhookCommand(
                request.eventId(),
                request.eventType(),
                request.sourceIncidentId(),
                sha256(rawBody)));
    return ResponseEntity.accepted().body(Mock112WebhookEventResponse.from(result));
  }

  private Mock112WebhookEventRequest parseRequest(String rawBody) {
    try {
      return objectMapper.readValue(rawBody, Mock112WebhookEventRequest.class);
    } catch (JsonProcessingException exception) {
      throw new IncidentApiException("mock112_event_invalid", HttpStatus.CONFLICT, exception);
    }
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }
}
