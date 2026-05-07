package com.surimap.marker.notification.service;

import com.surimap.marker.notification.domain.NotificationRecipientPolicy;
import com.surimap.marker.notification.domain.NotificationRecipients;
import com.surimap.marker.notification.domain.NotificationType;
import com.surimap.marker.notification.port.NotificationTargetPort;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationRecipientResolver {

  private final NotificationTargetPort notificationTargetPort;

  public NotificationRecipientResolver(NotificationTargetPort notificationTargetPort) {
    this.notificationTargetPort = Objects.requireNonNull(notificationTargetPort);
  }

  public NotificationRecipients resolve(UUID incidentId, NotificationType notificationType) {
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    NotificationRecipientPolicy policy = policyFor(notificationType);
    return notificationTargetPort.notificationTargets(incidentId, policy);
  }

  private NotificationRecipientPolicy policyFor(NotificationType notificationType) {
    return switch (Objects.requireNonNull(notificationType, "notificationType must not be null")) {
      case SUPPORT_REQUEST_CREATED -> NotificationRecipientPolicy.COMMANDERS_AND_FIELD_COMMANDERS;
      case PERSON_FOUND -> NotificationRecipientPolicy.ALL_INCIDENT_ASSIGNED;
    };
  }
}
