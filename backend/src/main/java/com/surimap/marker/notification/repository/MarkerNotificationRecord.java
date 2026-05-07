package com.surimap.marker.notification.repository;

import com.surimap.marker.notification.domain.MarkerNotificationStatus;
import com.surimap.marker.notification.domain.NotificationRecipientPolicy;
import com.surimap.marker.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MarkerNotificationRecord(
    UUID id,
    UUID markerId,
    NotificationType notificationType,
    NotificationRecipientPolicy recipientRule,
    List<String> recipientAccountIds,
    List<String> recipientPolicePhoneIds,
    String notificationPayloadJson,
    MarkerNotificationStatus status,
    long version,
    Instant createdAt) {

  public MarkerNotificationRecord {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(markerId, "markerId must not be null");
    Objects.requireNonNull(notificationType, "notificationType must not be null");
    Objects.requireNonNull(recipientRule, "recipientRule must not be null");
    recipientAccountIds =
        List.copyOf(
            Objects.requireNonNull(recipientAccountIds, "recipientAccountIds must not be null"));
    recipientPolicePhoneIds =
        List.copyOf(
            Objects.requireNonNull(
                recipientPolicePhoneIds, "recipientPolicePhoneIds must not be null"));
    Objects.requireNonNull(notificationPayloadJson, "notificationPayloadJson must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    if (version <= 0) {
      throw new IllegalArgumentException("version must be positive");
    }
  }
}
