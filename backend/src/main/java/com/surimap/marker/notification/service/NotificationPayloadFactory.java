package com.surimap.marker.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.marker.dto.MarkerNotificationPayload;
import com.surimap.marker.notification.domain.MarkerNotificationStatus;
import com.surimap.marker.notification.domain.NotificationRecipients;
import com.surimap.marker.notification.domain.NotificationType;
import com.surimap.policephone.PolicePhoneMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificationPayloadFactory {

  private final ObjectMapper objectMapper;
  private final PolicePhoneMapper policePhoneMapper;

  @Autowired
  public NotificationPayloadFactory(
      ObjectMapper objectMapper, ObjectProvider<PolicePhoneMapper> policePhoneMapperProvider) {
    this(
        objectMapper,
        policePhoneMapperProvider == null ? null : policePhoneMapperProvider.getIfAvailable());
  }

  private NotificationPayloadFactory(
      ObjectMapper objectMapper, PolicePhoneMapper policePhoneMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.policePhoneMapper = policePhoneMapper;
  }

  public MarkerNotificationPayload markerNotificationPayload(
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
    return MarkerNotificationPayload.builder()
        .id(notificationId)
        .markerId(context.markerId())
        .incidentId(context.incidentId())
        .opId(context.opId())
        .policePhoneId(context.policePhoneId())
        .status(status.name())
        .version(notificationVersion)
        .type(notificationType.name())
        .recipientPolicy(recipients.policy().name())
        .recipientAccountIds(recipients.accountIds())
        .recipientPolicePhoneIds(recipients.policePhoneIds())
        .markerType(context.markerType().name())
        .locationLabel(locationLabel(context))
        .policePhoneName(policePhoneName(context.policePhoneId()))
        .clientTs(context.clientTs())
        .build();
  }

  public String toJson(NotificationType notificationType, MarkerNotificationPayload payload) {
    Objects.requireNonNull(notificationType, "notificationType must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("type", notificationType.name());
    fields.put("id", payload.getId());
    fields.put("markerId", payload.getMarkerId());
    fields.put("incidentId", payload.getIncidentId());
    fields.put("opId", payload.getOpId());
    fields.put("policePhoneId", payload.getPolicePhoneId());
    fields.put("status", payload.getStatus());
    fields.put("version", payload.getVersion());
    fields.put("recipientPolicy", payload.getRecipientPolicy());
    fields.put("recipientAccountIds", payload.getRecipientAccountIds());
    fields.put("recipientPolicePhoneIds", payload.getRecipientPolicePhoneIds());
    fields.put("markerType", payload.getMarkerType());
    fields.put("locationLabel", payload.getLocationLabel());
    fields.put("policePhoneName", payload.getPolicePhoneName());
    if (payload.getClientTs() != null) {
      fields.put("clientTs", payload.getClientTs().toString());
    }
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
