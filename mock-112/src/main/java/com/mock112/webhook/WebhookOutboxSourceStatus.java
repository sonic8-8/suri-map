package com.mock112.webhook;

import java.time.OffsetDateTime;

public record WebhookOutboxSourceStatus(
        String sourceIncidentId,
        String status,
        int events,
        int sent,
        int pending,
        int failed,
        int attemptCount,
        OffsetDateTime updatedAt,
        String lastError) {
}
