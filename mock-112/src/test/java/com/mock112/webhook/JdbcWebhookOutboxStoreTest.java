package com.mock112.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mock112-webhook-outbox-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "suri-map.webhook.enabled=false"
})
@DisplayName("mock-112 JDBC webhook outbox store")
class JdbcWebhookOutboxStoreTest {

    @Autowired
    private WebhookOutboxStore store;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        store.reset();
    }

    @Test
    @DisplayName("webhook event를 outbox에 저장하고 상태별 카운트를 집계한다")
    void enqueueAndCountByStatus() throws Exception {
        SuriMapWebhookEvent event = new SuriMapWebhookEvent(
                "mock112:INCIDENT_READY:00000000-0000-0000-0000-000000000001",
                "INCIDENT_READY",
                "00000000-0000-0000-0000-000000000001",
                OffsetDateTime.parse("2026-05-20T09:00:00+09:00"),
                null);
        OffsetDateTime now = OffsetDateTime.parse("2026-05-20T09:01:00+09:00");

        WebhookOutboxRecord record = store.enqueue(event, objectMapper.writeValueAsString(event), now);
        WebhookOutboxRecord duplicate = store.enqueue(event, objectMapper.writeValueAsString(event), now.plusMinutes(1));

        assertThat(duplicate.eventId()).isEqualTo(record.eventId());
        assertThat(store.countByStatus()).containsEntry("PENDING", 1);
        List<WebhookOutboxRecord> due = store.findDue(now.plusSeconds(1), 10);
        assertThat(due).extracting(WebhookOutboxRecord::eventId).containsExactly(event.eventId());
    }

    @Test
    @DisplayName("성공과 실패 상태 변경은 attempt_count와 status를 갱신한다")
    void markSentAndFailedUpdateAttemptState() throws Exception {
        SuriMapWebhookEvent event = new SuriMapWebhookEvent(
                "mock112:INCIDENT_READY:00000000-0000-0000-0000-000000000002",
                "INCIDENT_READY",
                "00000000-0000-0000-0000-000000000002",
                OffsetDateTime.parse("2026-05-20T09:00:00+09:00"),
                null);
        OffsetDateTime now = OffsetDateTime.parse("2026-05-20T09:01:00+09:00");
        store.enqueue(event, objectMapper.writeValueAsString(event), now);

        store.markFailed(event.eventId(), "connect refused", now.plusSeconds(5), now.plusSeconds(1), false);
        WebhookOutboxRecord failedOnce = store.findByEventId(event.eventId()).orElseThrow();
        assertThat(failedOnce.status()).isEqualTo("PENDING");
        assertThat(failedOnce.attemptCount()).isEqualTo(1);
        assertThat(failedOnce.lastError()).contains("connect refused");

        store.markSent(event.eventId(), now.plusSeconds(2));
        WebhookOutboxRecord sent = store.findByEventId(event.eventId()).orElseThrow();
        assertThat(sent.status()).isEqualTo("SENT");
        assertThat(sent.attemptCount()).isEqualTo(2);
        assertThat(sent.sentAt()).isNotNull();
        assertThat(store.countByStatus()).containsEntry("SENT", 1);
    }

    @Test
    @DisplayName("sourceIncidentId별 최신 outbox 요약은 UI가 사건별 전송 상태를 볼 수 있게 한다")
    void summarizeBySourceIncidentIds() throws Exception {
        String sourceIncidentId = "00000000-0000-0000-0000-000000000003";
        SuriMapWebhookEvent ready = new SuriMapWebhookEvent(
                "mock112:INCIDENT_READY:" + sourceIncidentId,
                "INCIDENT_READY",
                sourceIncidentId,
                OffsetDateTime.parse("2026-05-20T09:00:00+09:00"),
                null);
        SuriMapWebhookEvent assignment = new SuriMapWebhookEvent(
                "mock112:ASSIGNMENT_CHANGED:00000000000000000000000000000003",
                "INCIDENT_ASSIGNMENT_CHANGED",
                sourceIncidentId,
                OffsetDateTime.parse("2026-05-20T09:01:00+09:00"),
                null);
        OffsetDateTime now = OffsetDateTime.parse("2026-05-20T09:02:00+09:00");

        store.enqueue(ready, objectMapper.writeValueAsString(ready), now);
        store.enqueue(assignment, objectMapper.writeValueAsString(assignment), now.plusSeconds(1));
        store.markSent(ready.eventId(), now.plusSeconds(2));
        store.markFailed(assignment.eventId(), "connect refused", now.plusSeconds(5), now.plusSeconds(3), false);

        Map<String, WebhookOutboxSourceStatus> summary =
                store.summarizeBySourceIncidentIds(List.of(sourceIncidentId, "missing-source"));

        assertThat(summary).containsOnlyKeys(sourceIncidentId);
        assertThat(summary.get(sourceIncidentId).status()).isEqualTo("PENDING");
        assertThat(summary.get(sourceIncidentId).events()).isEqualTo(2);
        assertThat(summary.get(sourceIncidentId).sent()).isEqualTo(1);
        assertThat(summary.get(sourceIncidentId).pending()).isEqualTo(1);
        assertThat(summary.get(sourceIncidentId).attemptCount()).isEqualTo(1);
    }
}
