package com.surimap.marker.notification.query;

import java.time.Instant;
import java.util.UUID;

public record MarkerNotificationToastRow(
    UUID notificationId,
    UUID markerId,
    UUID incidentId,
    UUID opId,
    UUID policePhoneId,
    String notificationType,
    String status,
    long version,
    Instant createdAt,
    UUID latestEventId) {}
