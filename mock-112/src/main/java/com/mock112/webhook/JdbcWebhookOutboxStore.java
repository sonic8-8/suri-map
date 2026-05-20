package com.mock112.webhook;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcWebhookOutboxStore implements WebhookOutboxStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWebhookOutboxStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public WebhookOutboxRecord enqueue(SuriMapWebhookEvent event, String payloadJson, OffsetDateTime now) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO mock_webhook_outbox
                        (event_id, event_type, source_incident_id, payload_json, status,
                         attempt_count, next_attempt_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
                    """,
                    event.eventId(),
                    event.eventType(),
                    event.sourceIncidentId(),
                    payloadJson,
                    now,
                    now,
                    now);
        } catch (DuplicateKeyException ignored) {
            // Deterministic event ids make repeated API calls converge on the same outbox row.
        }
        return findByEventId(event.eventId()).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WebhookOutboxRecord> findByEventId(String eventId) {
        List<WebhookOutboxRecord> records = jdbcTemplate.query(
                """
                SELECT event_id, event_type, source_incident_id, payload_json, status,
                       attempt_count, next_attempt_at, last_error, created_at, updated_at, sent_at
                FROM mock_webhook_outbox
                WHERE event_id = ?
                """,
                (rs, rowNum) -> mapRecord(rs),
                eventId);
        return records.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebhookOutboxRecord> findDue(OffsetDateTime now, int limit) {
        return jdbcTemplate.query(
                """
                SELECT event_id, event_type, source_incident_id, payload_json, status,
                       attempt_count, next_attempt_at, last_error, created_at, updated_at, sent_at
                FROM mock_webhook_outbox
                WHERE status = 'PENDING'
                  AND next_attempt_at <= ?
                ORDER BY next_attempt_at, created_at, event_id
                LIMIT ?
                """,
                (rs, rowNum) -> mapRecord(rs),
                now,
                limit);
    }

    @Override
    @Transactional
    public void markSent(String eventId, OffsetDateTime now) {
        jdbcTemplate.update(
                """
                UPDATE mock_webhook_outbox
                SET status = 'SENT',
                    attempt_count = attempt_count + 1,
                    next_attempt_at = ?,
                    last_error = NULL,
                    updated_at = ?,
                    sent_at = ?
                WHERE event_id = ?
                """,
                now,
                now,
                now,
                eventId);
    }

    @Override
    @Transactional
    public void markFailed(String eventId, String error, OffsetDateTime nextAttemptAt, OffsetDateTime now, boolean exhausted) {
        jdbcTemplate.update(
                """
                UPDATE mock_webhook_outbox
                SET status = ?,
                    attempt_count = attempt_count + 1,
                    next_attempt_at = ?,
                    last_error = ?,
                    updated_at = ?
                WHERE event_id = ?
                """,
                exhausted ? "FAILED" : "PENDING",
                nextAttemptAt,
                truncate(error),
                now,
                eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> countByStatus() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("PENDING", 0);
        counts.put("SENT", 0);
        counts.put("FAILED", 0);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT status, COUNT(*) AS count
                FROM mock_webhook_outbox
                GROUP BY status
                """);
        for (Map<String, Object> row : rows) {
            counts.put((String) row.get("status"), ((Number) row.get("count")).intValue());
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, WebhookOutboxSourceStatus> summarizeBySourceIncidentIds(Collection<String> sourceIncidentIds) {
        List<String> ids = sourceIncidentIds == null
                ? List.of()
                : sourceIncidentIds.stream()
                        .filter(id -> id != null && !id.isBlank())
                        .distinct()
                        .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        StringJoiner placeholders = new StringJoiner(", ");
        for (int i = 0; i < ids.size(); i++) {
            placeholders.add("?");
        }
        List<WebhookOutboxSourceStatus> rows = jdbcTemplate.query(
                """
                SELECT source_incident_id,
                       COUNT(*) AS events,
                       SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END) AS sent,
                       SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending,
                       SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
                       MAX(attempt_count) AS attempt_count,
                       MAX(updated_at) AS updated_at,
                       MAX(last_error) AS last_error
                FROM mock_webhook_outbox
                WHERE source_incident_id IN (%s)
                GROUP BY source_incident_id
                """.formatted(placeholders),
                (rs, rowNum) -> mapSourceStatus(rs),
                ids.toArray());
        Map<String, WebhookOutboxSourceStatus> bySource = new LinkedHashMap<>();
        for (WebhookOutboxSourceStatus row : rows) {
            bySource.put(row.sourceIncidentId(), row);
        }
        return bySource;
    }

    @Override
    @Transactional
    public void reset() {
        jdbcTemplate.update("DELETE FROM mock_webhook_outbox");
    }

    private WebhookOutboxRecord mapRecord(ResultSet rs) throws SQLException {
        return new WebhookOutboxRecord(
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getString("source_incident_id"),
                rs.getString("payload_json"),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                offsetDateTime(rs, "next_attempt_at"),
                rs.getString("last_error"),
                offsetDateTime(rs, "created_at"),
                offsetDateTime(rs, "updated_at"),
                offsetDateTime(rs, "sent_at"));
    }

    private WebhookOutboxSourceStatus mapSourceStatus(ResultSet rs) throws SQLException {
        int events = rs.getInt("events");
        int sent = rs.getInt("sent");
        int pending = rs.getInt("pending");
        int failed = rs.getInt("failed");
        String status = "UNKNOWN";
        if (failed > 0) {
            status = "FAILED";
        } else if (pending > 0) {
            status = "PENDING";
        } else if (events > 0 && sent == events) {
            status = "SENT";
        }
        return new WebhookOutboxSourceStatus(
                rs.getString("source_incident_id"),
                status,
                events,
                sent,
                pending,
                failed,
                rs.getInt("attempt_count"),
                offsetDateTime(rs, "updated_at"),
                rs.getString("last_error"));
    }

    private OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }
}
