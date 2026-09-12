package com.surimap.domain.marker;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarkerNotification {

  private UUID id;
  private UUID markerId;
  private MarkerNotificationType notificationType;
  private MarkerNotificationRecipientPolicy recipientRule;
  private List<String> recipientAccountIds;
  private List<String> recipientPolicePhoneIds;
  private String notificationPayloadJson;
  private MarkerNotificationStatus status;
  private long version;
  private Instant createdAt;

  @Builder
  private MarkerNotification(
      UUID id,
      UUID markerId,
      MarkerNotificationType notificationType,
      MarkerNotificationRecipientPolicy recipientRule,
      List<String> recipientAccountIds,
      List<String> recipientPolicePhoneIds,
      String notificationPayloadJson,
      MarkerNotificationStatus status,
      long version,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.markerId = Objects.requireNonNull(markerId, "markerId must not be null");
    this.notificationType =
        Objects.requireNonNull(notificationType, "notificationType must not be null");
    this.recipientRule = Objects.requireNonNull(recipientRule, "recipientRule must not be null");
    this.recipientAccountIds =
        List.copyOf(
            Objects.requireNonNull(recipientAccountIds, "recipientAccountIds must not be null"));
    this.recipientPolicePhoneIds =
        List.copyOf(
            Objects.requireNonNull(
                recipientPolicePhoneIds, "recipientPolicePhoneIds must not be null"));
    this.notificationPayloadJson =
        Objects.requireNonNull(notificationPayloadJson, "notificationPayloadJson must not be null");
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    if (version <= 0) {
      throw new IllegalArgumentException("version must be positive");
    }
    this.version = version;
  }
}
