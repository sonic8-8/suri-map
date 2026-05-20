package com.surimap.incident.service;

import com.surimap.eventhub.consumer.DomainEventConsumer;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.marker.notification.port.FcmDispatcherPort;
import com.surimap.policephone.query.FcmTokenQuery;
import com.surimap.policephone.query.FcmTokenRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IncidentClosedFcmDispatchConsumer implements DomainEventConsumer {

  private static final String INCIDENT_CLOSED = "INCIDENT_CLOSED";
  private static final Logger log = LoggerFactory.getLogger(IncidentClosedFcmDispatchConsumer.class);

  private final IncidentMapper incidentMapper;
  private final FcmTokenQuery fcmTokenQuery;
  private final FcmDispatcherPort fcmDispatcher;

  public IncidentClosedFcmDispatchConsumer(
      IncidentMapper incidentMapper, FcmTokenQuery fcmTokenQuery, FcmDispatcherPort fcmDispatcher) {
    this.incidentMapper = incidentMapper;
    this.fcmTokenQuery = fcmTokenQuery;
    this.fcmDispatcher = fcmDispatcher;
  }

  @Override
  public boolean supports(PublishRequest event) {
    return event != null && INCIDENT_CLOSED.equals(event.type());
  }

  @Override
  public void consume(PublishRequest event) {
    List<UUID> accountIds = parseAccountIds(incidentMapper.findActiveAssignmentAccountIds(event.incidentId()));
    if (accountIds.isEmpty()) {
      return;
    }

    List<FcmTokenRow> tokens = fcmTokenQuery.activeByAccounts(accountIds);
    List<String> recipients =
        tokens.stream().map(FcmTokenRow::tokenCiphertext).map(this::decryptToken).distinct().toList();
    if (recipients.isEmpty()) {
      return;
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", INCIDENT_CLOSED);
    payload.put("incidentId", event.incidentId().toString());
    payload.put("status", stringPayload(event, "status", "CLOSED"));
    payload.put("version", stringPayload(event, "version", ""));
    payload.put("closedAt", stringPayload(event, "closedAt", event.occurredAt().toString()));
    payload.put("writeDisabledReason", stringPayload(event, "writeDisabledReason", "incident_closed"));
    payload.put("recipientAccountIds", accountIds.stream().map(UUID::toString).toList());
    payload.put("recipientPolicePhoneIds", tokens.stream().map(FcmTokenRow::policePhoneId).map(UUID::toString).toList());

    String eventId = event.eventId().toString();
    try {
      fcmDispatcher.send(recipients, payload, eventId);
    } catch (RuntimeException exception) {
      log.warn("failed to dispatch incident closed FCM eventId={}", eventId, exception);
    }
  }

  private String stringPayload(PublishRequest event, String key, String fallback) {
    Object value = event.payload().get(key);
    if (value == null) {
      return fallback;
    }
    return String.valueOf(value);
  }

  private List<UUID> parseAccountIds(List<String> accountIds) {
    if (accountIds == null) {
      return List.of();
    }
    return accountIds.stream()
        .map(this::parseUuidOrNull)
        .filter(Objects::nonNull)
        .toList();
  }

  private UUID parseUuidOrNull(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private String decryptToken(String tokenCiphertext) {
    if (tokenCiphertext == null) {
      return "";
    }
    return tokenCiphertext.startsWith("cipher:")
        ? tokenCiphertext.substring("cipher:".length())
        : tokenCiphertext;
  }
}
