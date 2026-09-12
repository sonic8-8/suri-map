package com.surimap.domain.marker;

public enum MarkerNotificationType {
  SUPPORT_REQUEST_CREATED,
  PERSON_FOUND;

  public MarkerNotificationRecipientPolicy getRecipientPolicy() {
    return switch (this) {
      case SUPPORT_REQUEST_CREATED ->
          MarkerNotificationRecipientPolicy.COMMANDERS_AND_FIELD_COMMANDERS;
      case PERSON_FOUND -> MarkerNotificationRecipientPolicy.ALL_INCIDENT_ASSIGNED;
    };
  }
}
