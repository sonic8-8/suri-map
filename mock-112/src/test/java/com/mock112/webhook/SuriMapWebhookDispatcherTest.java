package com.mock112.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withAccepted;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mock112.domain.MockIncident;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;

@DisplayName("mock-112 Suri-Map webhook dispatcher")
class SuriMapWebhookDispatcherTest {

    @Test
    @DisplayName("assignment changed eventId is deterministic and bounded for backend idempotency key")
    void assignmentChangedEventIdIsDeterministicAndBounded() {
        String sourceIncidentId = "00000000-0000-0000-0000-000000000001";
        String longEventKey =
                "acct-support-team:external-assignment-key-that-can-grow-past-the-backend-event-id-column";

        String eventId = SuriMapWebhookDispatcher.assignmentChangedEventId(sourceIncidentId, longEventKey);

        assertThat(eventId).isEqualTo(SuriMapWebhookDispatcher.assignmentChangedEventId(sourceIncidentId, longEventKey));
        assertThat(eventId).startsWith("mock112:ASSIGNMENT_CHANGED:");
        assertThat(eventId).hasSizeLessThanOrEqualTo(120);
    }

    @Test
    @DisplayName("webhook 비활성 상태에서는 outbox 저장 없이 delivery disabled를 반환한다")
    void disabledWebhookDoesNotEnqueue() {
        InMemoryWebhookOutboxStore outboxStore = new InMemoryWebhookOutboxStore();
        SuriMapWebhookDispatcher dispatcher = newDispatcher(disabledProperties(), outboxStore, new RestTemplate());
        MockIncident incident = new MockIncident();
        incident.setSourceIncidentId("00000000-0000-0000-0000-000000000001");

        WebhookDeliveryResult result = dispatcher.sendIncidentReady(incident);

        assertThat(result.status()).isEqualTo("DISABLED");
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(outboxStore.countByStatus()).containsEntry("PENDING", 0);
    }

    @Test
    @DisplayName("전송 성공 시 webhook event를 outbox에 저장한 뒤 SENT로 표시한다")
    void successfulSendMarksOutboxSent() {
        InMemoryWebhookOutboxStore outboxStore = new InMemoryWebhookOutboxStore();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        SuriMapWebhookProperties properties = enabledProperties();
        SuriMapWebhookDispatcher dispatcher = newDispatcher(properties, outboxStore, restTemplate);
        MockIncident incident = new MockIncident();
        incident.setSourceIncidentId("00000000-0000-0000-0000-000000000001");

        server.expect(requestTo(properties.getUrl()))
                .andExpect(header("X-Client-Channel", "INTERNAL"))
                .andExpect(header("Idempotency-Key", "mock112:INCIDENT_READY:" + incident.getSourceIncidentId()))
                .andRespond(withAccepted());

        WebhookDeliveryResult result = dispatcher.sendIncidentReady(incident);

        assertThat(result.status()).isEqualTo("SENT");
        WebhookOutboxRecord record = outboxStore.findByEventId("mock112:INCIDENT_READY:" + incident.getSourceIncidentId()).orElseThrow();
        assertThat(record.status()).isEqualTo("SENT");
        assertThat(record.attemptCount()).isEqualTo(1);
        server.verify();
    }

    @Test
    @DisplayName("전송 실패 event는 PENDING으로 남고 retryPending에서 재전송된다")
    void failedSendIsRetriedFromOutbox() {
        InMemoryWebhookOutboxStore outboxStore = new InMemoryWebhookOutboxStore();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        SuriMapWebhookProperties properties = enabledProperties();
        properties.setRetryDelayMs(0);
        properties.setMaxAttempts(3);
        SuriMapWebhookDispatcher dispatcher = newDispatcher(properties, outboxStore, restTemplate);
        MockIncident incident = new MockIncident();
        incident.setSourceIncidentId("00000000-0000-0000-0000-000000000002");
        String eventId = "mock112:INCIDENT_READY:" + incident.getSourceIncidentId();

        server.expect(requestTo(properties.getUrl())).andRespond(withServerError());
        server.expect(requestTo(properties.getUrl())).andRespond(withAccepted());

        WebhookDeliveryResult first = dispatcher.sendIncidentReady(incident);
        WebhookDeliveryResult retry = dispatcher.retryPending();

        assertThat(first.status()).isEqualTo("PENDING");
        assertThat(retry.status()).isEqualTo("SENT");
        WebhookOutboxRecord record = outboxStore.findByEventId(eventId).orElseThrow();
        assertThat(record.status()).isEqualTo("SENT");
        assertThat(record.attemptCount()).isEqualTo(2);
        server.verify();
    }

    private SuriMapWebhookDispatcher newDispatcher(
            SuriMapWebhookProperties properties,
            WebhookOutboxStore outboxStore,
            RestTemplate restTemplate) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new SuriMapWebhookDispatcher(properties, mapper, outboxStore, restTemplate);
    }

    private SuriMapWebhookProperties enabledProperties() {
        SuriMapWebhookProperties properties = new SuriMapWebhookProperties();
        properties.setEnabled(true);
        properties.setUrl("http://suri-map.local/api/internal/mock-112/events");
        properties.setRetryBatchSize(20);
        properties.setMaxAttempts(5);
        properties.setRetryDelayMs(5000);
        return properties;
    }

    private SuriMapWebhookProperties disabledProperties() {
        SuriMapWebhookProperties properties = new SuriMapWebhookProperties();
        properties.setEnabled(false);
        return properties;
    }

    private static class InMemoryWebhookOutboxStore implements WebhookOutboxStore {

        private final Map<String, WebhookOutboxRecord> records = new LinkedHashMap<>();

        @Override
        public WebhookOutboxRecord enqueue(SuriMapWebhookEvent event, String payloadJson, OffsetDateTime now) {
            records.putIfAbsent(event.eventId(), new WebhookOutboxRecord(
                    event.eventId(),
                    event.eventType(),
                    event.sourceIncidentId(),
                    payloadJson,
                    "PENDING",
                    0,
                    now,
                    null,
                    now,
                    now,
                    null));
            return records.get(event.eventId());
        }

        @Override
        public Optional<WebhookOutboxRecord> findByEventId(String eventId) {
            return Optional.ofNullable(records.get(eventId));
        }

        @Override
        public List<WebhookOutboxRecord> findDue(OffsetDateTime now, int limit) {
            return records.values().stream()
                    .filter(record -> "PENDING".equals(record.status()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void markSent(String eventId, OffsetDateTime now) {
            WebhookOutboxRecord record = records.get(eventId);
            records.put(eventId, new WebhookOutboxRecord(
                    record.eventId(),
                    record.eventType(),
                    record.sourceIncidentId(),
                    record.payloadJson(),
                    "SENT",
                    record.attemptCount() + 1,
                    now,
                    null,
                    record.createdAt(),
                    now,
                    now));
        }

        @Override
        public void markFailed(String eventId, String error, OffsetDateTime nextAttemptAt, OffsetDateTime now, boolean exhausted) {
            WebhookOutboxRecord record = records.get(eventId);
            records.put(eventId, new WebhookOutboxRecord(
                    record.eventId(),
                    record.eventType(),
                    record.sourceIncidentId(),
                    record.payloadJson(),
                    exhausted ? "FAILED" : "PENDING",
                    record.attemptCount() + 1,
                    nextAttemptAt,
                    error,
                    record.createdAt(),
                    now,
                    record.sentAt()));
        }

        @Override
        public Map<String, Integer> countByStatus() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("PENDING", 0);
            counts.put("SENT", 0);
            counts.put("FAILED", 0);
            for (WebhookOutboxRecord record : records.values()) {
                counts.computeIfPresent(record.status(), (ignored, count) -> count + 1);
            }
            return counts;
        }

        @Override
        public void reset() {
            records.clear();
        }
    }
}
