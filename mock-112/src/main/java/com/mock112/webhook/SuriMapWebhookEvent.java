package com.mock112.webhook;

import java.time.OffsetDateTime;

public record SuriMapWebhookEvent(
        String eventId,
        String eventType,
        String sourceIncidentId,
        OffsetDateTime occurredAt) {
}
