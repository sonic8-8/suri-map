package com.surimap.marker.notification.adapter;

import com.surimap.marker.notification.domain.NotificationRecipientPolicy;
import com.surimap.marker.notification.domain.NotificationRecipients;
import com.surimap.marker.notification.port.NotificationTargetPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Fail-closed adapter until S1-1 provides IncidentAssignmentView.notificationTargets.
 *
 * <p>L5 owns recipient policy and payload shape, but not the membership source.
 */
@Component
public class BlockingNotificationTargetAdapter implements NotificationTargetPort {

  @Override
  public NotificationRecipients notificationTargets(
      UUID incidentId, NotificationRecipientPolicy recipientPolicy) {
    throw new IllegalStateException("notification target provider is not configured");
  }
}
