package com.surimap.marker.notification.port;

import com.surimap.marker.notification.domain.NotificationRecipientPolicy;
import com.surimap.marker.notification.domain.NotificationRecipients;
import java.util.UUID;

/** S1-1 incident assignment notification-target view consumed by S5. */
public interface NotificationTargetPort {

  NotificationRecipients notificationTargets(
      UUID incidentId, NotificationRecipientPolicy recipientPolicy);
}
