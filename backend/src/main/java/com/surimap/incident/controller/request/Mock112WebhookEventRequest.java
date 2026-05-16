package com.surimap.incident.controller.request;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Mock112WebhookEventRequest(
    String eventId,
    String eventType,
    UUID sourceIncidentId,
    OffsetDateTime occurredAt) {}
