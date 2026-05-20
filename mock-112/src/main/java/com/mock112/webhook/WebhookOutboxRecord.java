package com.mock112.webhook;

import java.time.OffsetDateTime;

public record WebhookOutboxRecord(
        String eventId,
        String eventType,
        String sourceIncidentId,
        String payloadJson,
        String status,
        int attemptCount,
        OffsetDateTime nextAttemptAt,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime sentAt) {
}
