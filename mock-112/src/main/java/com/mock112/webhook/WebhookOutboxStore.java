package com.mock112.webhook;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WebhookOutboxStore {

    WebhookOutboxRecord enqueue(SuriMapWebhookEvent event, String payloadJson, OffsetDateTime now);

    Optional<WebhookOutboxRecord> findByEventId(String eventId);

    List<WebhookOutboxRecord> findDue(OffsetDateTime now, int limit);

    void markSent(String eventId, OffsetDateTime now);

    void markFailed(String eventId, String error, OffsetDateTime nextAttemptAt, OffsetDateTime now, boolean exhausted);

    Map<String, Integer> countByStatus();

    Map<String, WebhookOutboxSourceStatus> summarizeBySourceIncidentIds(Collection<String> sourceIncidentIds);

    void reset();
}
