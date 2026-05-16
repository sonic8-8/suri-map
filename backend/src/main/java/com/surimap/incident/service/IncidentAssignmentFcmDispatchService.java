package com.surimap.incident.service;

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
import org.springframework.stereotype.Service;

@Service
public class IncidentAssignmentFcmDispatchService {

  private static final Logger log = LoggerFactory.getLogger(IncidentAssignmentFcmDispatchService.class);

  private final FcmTokenQuery fcmTokenQuery;
  private final FcmDispatcherPort fcmDispatcher;

  public IncidentAssignmentFcmDispatchService(
      FcmTokenQuery fcmTokenQuery, FcmDispatcherPort fcmDispatcher) {
    this.fcmTokenQuery = fcmTokenQuery;
    this.fcmDispatcher = fcmDispatcher;
  }

  public void dispatchAssignmentChanged(IncidentAssignmentImportResult result) {
    List<UUID> accountIds = parseAccountIds(result.changedAccountIds());
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
    payload.put("type", "INCIDENT_ASSIGNMENT_CHANGED");
    payload.put("incidentId", result.incidentId().toString());
    payload.put("status", result.status());
    payload.put("version", result.version());
    payload.put("recipientAccountIds", result.changedAccountIds());
    payload.put("recipientPolicePhoneIds", tokens.stream().map(FcmTokenRow::policePhoneId).map(UUID::toString).toList());
    String eventId = "fcm:INCIDENT_ASSIGNMENT_CHANGED:" + result.incidentId() + ":v" + result.version();
    try {
      fcmDispatcher.send(recipients, payload, eventId);
    } catch (RuntimeException exception) {
      log.warn("failed to dispatch assignment FCM eventId={}", eventId, exception);
    }
  }

  private List<UUID> parseAccountIds(List<String> accountIds) {
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
