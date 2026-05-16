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

    public SuriMapWebhookDispatcher(
            SuriMapWebhookProperties properties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public void sendIncidentReady(MockIncident incident) {
        send(new SuriMapWebhookEvent(
                "mock112:INCIDENT_READY:" + incident.getSourceIncidentId(),
                "INCIDENT_READY",
                incident.getSourceIncidentId(),
                OffsetDateTime.now()));
    }

    public void sendAssignmentChanged(String sourceIncidentId, List<MockAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }
        for (MockAssignment assignment : assignments) {
            String assignmentKey = assignment.getExternalAssignmentKey();
            String eventKey = assignmentKey == null || assignmentKey.isBlank()
                    ? assignment.getAccountCode()
                    : assignmentKey;
            send(new SuriMapWebhookEvent(
                    assignmentChangedEventId(sourceIncidentId, eventKey),
                    "INCIDENT_ASSIGNMENT_CHANGED",
                    sourceIncidentId,
                    OffsetDateTime.now()));
        }
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

    private void send(SuriMapWebhookEvent event) {
        if (!properties.isEnabled() || properties.getUrl() == null || properties.getUrl().isBlank()) {
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(event);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Client-Channel", "INTERNAL");
            headers.set("Idempotency-Key", event.eventId());
            if (properties.getSecret() != null && !properties.getSecret().isBlank()) {
                headers.set("X-Mock112-Signature", "sha256=" + hmacSha256(body));
            }
            restTemplate.postForEntity(properties.getUrl(), new HttpEntity<>(body, headers), String.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize Suri-Map webhook event", exception);
        } catch (RestClientException exception) {
            log.warn("failed to send Suri-Map webhook eventId={}", event.eventId(), exception);
        }
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
