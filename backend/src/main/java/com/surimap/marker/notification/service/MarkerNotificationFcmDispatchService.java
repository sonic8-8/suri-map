package com.surimap.marker.notification.service;

import com.surimap.marker.dto.MarkerNotificationPublishRequestPayload;
import com.surimap.marker.event.MarkerEventIds;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MarkerNotificationFcmDispatchService {

  private static final Logger log =
      LoggerFactory.getLogger(MarkerNotificationFcmDispatchService.class);

  private final FcmTokenQuery fcmTokenQuery;
  private final FcmDispatcherPort fcmDispatcher;

  public MarkerNotificationFcmDispatchService(
      FcmTokenQuery fcmTokenQuery, FcmDispatcherPort fcmDispatcher) {
    this.fcmTokenQuery = Objects.requireNonNull(fcmTokenQuery, "fcmTokenQuery must not be null");
    this.fcmDispatcher = Objects.requireNonNull(fcmDispatcher, "fcmDispatcher must not be null");
  }

  public void dispatchAfterCommit(
      String eventType, MarkerNotificationPublishRequestPayload payload) {
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
    Runnable dispatch = () -> dispatch(eventType, payload);
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      dispatch.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            dispatch.run();
          }
        });
  }

  private void dispatch(String eventType, MarkerNotificationPublishRequestPayload payload) {
    List<String> recipients =
        payload.recipientPolicePhoneIds().stream()
            .map(MarkerNotificationFcmDispatchService::parseUuid)
            .filter(Objects::nonNull)
            .flatMap(policePhoneId -> fcmTokenQuery.activeByPolicePhone(policePhoneId).stream())
            .map(FcmTokenRow::tokenCiphertext)
            .map(this::decryptToken)
            .filter(token -> !token.isBlank())
            .distinct()
            .toList();
    if (recipients.isEmpty()) {
      return;
    }
    String eventId = MarkerEventIds.eventId(eventType, payload.id(), payload.version()).toString();
    try {
      fcmDispatcher.send(recipients, payloadMap(eventType, payload), eventId);
    } catch (RuntimeException exception) {
      log.warn("failed to dispatch marker notification FCM eventId={}", eventId, exception);
    }
  }

  private static Map<String, Object> payloadMap(
      String eventType, MarkerNotificationPublishRequestPayload payload) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("type", eventType);
    values.put("id", payload.id().toString());
    values.put("markerId", payload.markerId().toString());
    values.put("incidentId", payload.incidentId().toString());
    values.put("opId", payload.opId().toString());
    values.put("policePhoneId", payload.policePhoneId().toString());
    values.put("status", payload.status());
    values.put("version", payload.version());
    values.put("recipientPolicy", payload.recipientPolicy());
    values.put("recipientAccountIds", payload.recipientAccountIds());
    values.put("recipientPolicePhoneIds", payload.recipientPolicePhoneIds());
    values.put("markerType", payload.markerType());
    putIfPresent(values, "locationLabel", payload.locationLabel());
    return values;
  }

  private static void putIfPresent(Map<String, Object> values, String key, Object value) {
    if (value != null) {
      values.put(key, value);
    }
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (RuntimeException exception) {
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
