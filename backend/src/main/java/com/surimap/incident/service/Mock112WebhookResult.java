package com.surimap.incident.service;

import java.util.UUID;

public record Mock112WebhookResult(
    String eventId,
    String eventType,
    UUID sourceIncidentId,
    UUID incidentId,
    String status,
    long version) {}
