package com.surimap.marker.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.marker.dto.MarkerNotificationPublishRequestPayload;
import com.surimap.marker.notification.domain.MarkerNotificationStatus;
import com.surimap.marker.notification.domain.NotificationRecipients;
import com.surimap.marker.notification.domain.NotificationType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationPayloadFactory {

  private final ObjectMapper objectMapper;

  public NotificationPayloadFactory(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
  }

  public MarkerNotificationPublishRequestPayload supportRequestPayload(
      UUID notificationId,
      MarkerNotificationContext context,
      NotificationRecipients recipients,
      MarkerNotificationStatus status,
      long notificationVersion) {
    Objects.requireNonNull(notificationId, "notificationId must not be null");
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(recipients, "recipients must not be null");
    Objects.requireNonNull(status, "status must not be null");
    return new MarkerNotificationPublishRequestPayload(
        notificationId,
        context.markerId(),
        context.incidentId(),
        context.opId(),
        context.policePhoneId(),
        status.name(),
        notificationVersion,
        NotificationType.SUPPORT_REQUEST_CREATED.name(),
        recipients.policy().name(),
        recipients.accountIds(),
        recipients.policePhoneIds(),
        context.markerType().name(),
        locationLabel(context));
  }

  public String toJson(
      NotificationType notificationType, MarkerNotificationPublishRequestPayload payload) {
    Objects.requireNonNull(notificationType, "notificationType must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("type", notificationType.name());
    fields.put("id", payload.id());
    fields.put("markerId", payload.markerId());
    fields.put("incidentId", payload.incidentId());
    fields.put("opId", payload.opId());
    fields.put("policePhoneId", payload.policePhoneId());
    fields.put("status", payload.status());
    fields.put("version", payload.version());
    fields.put("recipientPolicy", payload.recipientPolicy());
    fields.put("recipientAccountIds", payload.recipientAccountIds());
    fields.put("recipientPolicePhoneIds", payload.recipientPolicePhoneIds());
    fields.put("markerType", payload.markerType());
    fields.put("locationLabel", payload.locationLabel());
    try {
      return objectMapper.writeValueAsString(fields);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("failed to serialize marker notification payload", exception);
    }
  }

  private String locationLabel(MarkerNotificationContext context) {
    BigDecimal lon = context.location().coordinates().get(0);
    BigDecimal lat = context.location().coordinates().get(1);
    return lon.toPlainString() + "," + lat.toPlainString();
  }
}
