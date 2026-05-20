package com.surimap.marker.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.marker.dto.MarkerNotificationPublishRequestPayload;
import com.surimap.marker.notification.domain.MarkerNotificationStatus;
import com.surimap.marker.notification.domain.NotificationRecipients;
import com.surimap.marker.notification.domain.NotificationType;
import com.surimap.policephone.PolicePhoneMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class NotificationPayloadFactory {

  private final ObjectMapper objectMapper;
  private final PolicePhoneMapper policePhoneMapper;

  public NotificationPayloadFactory(ObjectMapper objectMapper) {
    this(objectMapper, (PolicePhoneMapper) null);
  }

  @Autowired
  public NotificationPayloadFactory(
      ObjectMapper objectMapper, ObjectProvider<PolicePhoneMapper> policePhoneMapperProvider) {
    this(objectMapper, policePhoneMapperProvider == null ? null : policePhoneMapperProvider.getIfAvailable());
  }

  private NotificationPayloadFactory(ObjectMapper objectMapper, PolicePhoneMapper policePhoneMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.policePhoneMapper = policePhoneMapper;
  }

  public MarkerNotificationPublishRequestPayload supportRequestPayload(
      UUID notificationId,
      MarkerNotificationContext context,
      NotificationRecipients recipients,
      MarkerNotificationStatus status,
      long notificationVersion) {
    return markerNotificationPayload(
        NotificationType.SUPPORT_REQUEST_CREATED,
        notificationId,
        context,
        recipients,
        status,
        notificationVersion);
  }

  public MarkerNotificationPublishRequestPayload markerNotificationPayload(
      NotificationType notificationType,
      UUID notificationId,
      MarkerNotificationContext context,
      NotificationRecipients recipients,
      MarkerNotificationStatus status,
      long notificationVersion) {
    Objects.requireNonNull(notificationType, "notificationType must not be null");
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
        notificationType.name(),
        recipients.policy().name(),
        recipients.accountIds(),
        recipients.policePhoneIds(),
        context.markerType().name(),
        locationLabel(context),
        policePhoneName(context.policePhoneId()));
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
    fields.put("policePhoneName", payload.policePhoneName());
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

  private String policePhoneName(UUID policePhoneId) {
    if (policePhoneMapper == null || policePhoneId == null) {
      return null;
    }
    return policePhoneMapper.findDisplayNameById(policePhoneId).orElse(null);
  }
}
