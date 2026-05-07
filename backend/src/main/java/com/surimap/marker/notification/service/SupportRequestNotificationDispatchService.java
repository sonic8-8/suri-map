package com.surimap.marker.notification.service;

import com.surimap.marker.notification.domain.NotificationType;
import com.surimap.marker.notification.port.FcmDispatcherPort;
import com.surimap.marker.notification.port.FcmDispatcherPort.DispatchResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SupportRequestNotificationDispatchService {

  private final FcmDispatcherPort fcmDispatcher;

  public SupportRequestNotificationDispatchService(FcmDispatcherPort fcmDispatcher) {
    this.fcmDispatcher = Objects.requireNonNull(fcmDispatcher, "fcmDispatcher must not be null");
  }

  public BoardToastEvidence dispatch(
      String eventId, List<String> recipients, Map<String, Object> payload) {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(recipients, "recipients must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
    validateMarkerNotification(payload);

    BoardToastEvidence evidence = BoardToastEvidence.fromNotificationPayload(eventId, payload);
    DispatchResult result = fcmDispatcher.send(recipients, payload, eventId);
    if (!result.isFullySuccessful()) {
      throw new IllegalStateException("FCM dispatch failed for eventId " + eventId);
    }
    return evidence;
  }

  private void validateMarkerNotification(Map<String, Object> payload) {
    Object type = payload.get("type");
    if (!NotificationType.SUPPORT_REQUEST_CREATED.name().equals(type)
        && !NotificationType.PERSON_FOUND.name().equals(type)) {
      throw new IllegalArgumentException("payload.type must be marker notification type");
    }
  }
}
