package com.surimap.incident.controller.response;

import com.surimap.incident.service.Mock112WebhookResult;
import java.util.UUID;

public record Mock112WebhookEventResponse(
    String eventId,
    String eventType,
    UUID sourceIncidentId,
    UUID incidentId,
    String status,
    long version) {

  public static Mock112WebhookEventResponse from(Mock112WebhookResult result) {
    return new Mock112WebhookEventResponse(
        result.eventId(),
        result.eventType(),
        result.sourceIncidentId(),
        result.incidentId(),
        result.status(),
        result.version());
  }
}
