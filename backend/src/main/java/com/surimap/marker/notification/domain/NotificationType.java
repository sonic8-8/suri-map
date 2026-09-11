package com.surimap.marker.notification.domain;

public enum NotificationType {
  SUPPORT_REQUEST_CREATED,
  PERSON_FOUND;

  public NotificationRecipientPolicy getRecipientPolicy() {
    return switch (this) {
      case SUPPORT_REQUEST_CREATED -> NotificationRecipientPolicy.COMMANDERS_AND_FIELD_COMMANDERS;
      case PERSON_FOUND -> NotificationRecipientPolicy.ALL_INCIDENT_ASSIGNED;
    };
  }
}
