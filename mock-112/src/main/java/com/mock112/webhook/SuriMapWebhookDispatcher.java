package com.mock112.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock112.domain.MockAssignment;
import com.mock112.domain.MockIncident;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class SuriMapWebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SuriMapWebhookDispatcher.class);

    private final SuriMapWebhookProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final WebhookOutboxStore outboxStore;

    @Autowired
    public SuriMapWebhookDispatcher(
            SuriMapWebhookProperties properties,
            ObjectMapper objectMapper,
            WebhookOutboxStore outboxStore) {
        this(properties, objectMapper, outboxStore, new RestTemplate());
    }

    SuriMapWebhookDispatcher(
            SuriMapWebhookProperties properties,
            ObjectMapper objectMapper,
            WebhookOutboxStore outboxStore,
            RestTemplate restTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.outboxStore = outboxStore;
        this.restTemplate = restTemplate;
    }

    public WebhookDeliveryResult sendIncidentReady(MockIncident incident) {
        return send(new SuriMapWebhookEvent(
                "mock112:INCIDENT_READY:" + incident.getSourceIncidentId(),
                "INCIDENT_READY",
                incident.getSourceIncidentId(),
                OffsetDateTime.now()));
    }

    public WebhookDeliveryResult sendAssignmentChanged(String sourceIncidentId, List<MockAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return WebhookDeliveryResult.none();
        }
        WebhookDeliveryResult result = WebhookDeliveryResult.none();
        for (MockAssignment assignment : assignments) {
            String assignmentKey = assignment.getExternalAssignmentKey();
            String eventKey = assignmentKey == null || assignmentKey.isBlank()
                    ? assignment.getAccountCode()
                    : assignmentKey;
            result = result.plus(send(new SuriMapWebhookEvent(
                    assignmentChangedEventId(sourceIncidentId, eventKey),
                    "INCIDENT_ASSIGNMENT_CHANGED",
                    sourceIncidentId,
                    OffsetDateTime.now())));
        }
        return result;
    }

    public WebhookDeliveryResult retryPending() {
        if (!isDeliveryEnabled()) {
            return WebhookDeliveryResult.none();
        }
        List<WebhookOutboxRecord> dueRecords =
                outboxStore.findDue(OffsetDateTime.now(), Math.max(1, properties.getRetryBatchSize()));
        WebhookDeliveryResult result = WebhookDeliveryResult.none();
        for (WebhookOutboxRecord record : dueRecords) {
            result = result.plus(attempt(record));
        }
        return result;
    }

    static String assignmentChangedEventId(String sourceIncidentId, String eventKey) {
        String rawKey = sourceIncidentId + ":" + eventKey;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
            return "mock112:ASSIGNMENT_CHANGED:" + hash.substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private WebhookDeliveryResult send(SuriMapWebhookEvent event) {
        try {
            String body = objectMapper.writeValueAsString(event);
            if (!isDeliveryEnabled()) {
                return WebhookDeliveryResult.skipped(1);
            }
            WebhookOutboxRecord record = outboxStore.enqueue(event, body, OffsetDateTime.now());
            if ("SENT".equals(record.status())) {
                return WebhookDeliveryResult.sent();
            }
            return attempt(record);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize Suri-Map webhook event", exception);
        }
    }

    private WebhookDeliveryResult attempt(WebhookOutboxRecord record) {
        try {
            restTemplate.postForEntity(
                    properties.getUrl(),
                    new HttpEntity<>(record.payloadJson(), headers(record)),
                    String.class);
            outboxStore.markSent(record.eventId(), OffsetDateTime.now());
            return WebhookDeliveryResult.sent();
        } catch (RestClientException exception) {
            OffsetDateTime now = OffsetDateTime.now();
            int nextAttemptCount = record.attemptCount() + 1;
            boolean exhausted = nextAttemptCount >= Math.max(1, properties.getMaxAttempts());
            outboxStore.markFailed(
                    record.eventId(),
                    exception.getMessage(),
                    now.plusNanos(Math.max(0, properties.getRetryDelayMs()) * 1_000_000),
                    now,
                    exhausted);
            log.warn(
                    "failed to send Suri-Map webhook eventId={} attempt={} exhausted={}",
                    record.eventId(),
                    nextAttemptCount,
                    exhausted,
                    exception);
            return exhausted ? WebhookDeliveryResult.failed() : WebhookDeliveryResult.pending();
        }
    }

    private HttpHeaders headers(WebhookOutboxRecord record) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Client-Channel", "INTERNAL");
        headers.set("Idempotency-Key", record.eventId());
        if (properties.getSecret() != null && !properties.getSecret().isBlank()) {
            headers.set("X-Mock112-Signature", "sha256=" + hmacSha256(record.payloadJson()));
        }
        return headers;
    }

    private boolean isDeliveryEnabled() {
        return properties.isEnabled() && properties.getUrl() != null && !properties.getUrl().isBlank();
    }

    private String hmacSha256(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }
}
