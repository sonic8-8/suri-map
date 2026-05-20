package com.surimap.incident.service;

import java.util.UUID;

public record Mock112WebhookCommand(
    String eventId,
    String eventType,
    UUID sourceIncidentId,
    String requestBodyHash,
    String closeReason) {}
