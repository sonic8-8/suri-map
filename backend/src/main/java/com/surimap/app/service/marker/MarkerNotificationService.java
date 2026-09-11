package com.surimap.app.service.marker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.account.AccountIdentityCatalog;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerNotification;
import com.surimap.domain.marker.MarkerNotificationMapper;
import com.surimap.global.event.MarkerEventIds;
import com.surimap.global.event.MarkerEventPublisher;
import com.surimap.global.event.MarkerNotificationPayload;
import com.surimap.incident.service.IncidentAssignmentView;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.notification.domain.MarkerNotificationStatus;
import com.surimap.marker.notification.domain.NotificationRecipients;
import com.surimap.marker.notification.domain.NotificationType;
import com.surimap.marker.notification.port.FcmDispatcherPort;
import com.surimap.policephone.PolicePhoneMapper;
import com.surimap.policephone.query.FcmTokenQuery;
import com.surimap.policephone.query.FcmTokenRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MarkerNotificationService {

  private static final Logger log = LoggerFactory.getLogger(MarkerNotificationService.class);
  private static final long INITIAL_NOTIFICATION_VERSION = 1L;

  private final MarkerNotificationMapper markerNotificationMapper;
  private final IncidentAssignmentView incidentAssignmentView;
  private final ObjectMapper objectMapper;
  private final PolicePhoneMapper policePhoneMapper;
  private final MarkerEventPublisher markerEventPublisher;
  private final FcmTokenQuery fcmTokenQuery;
  private final FcmDispatcherPort fcmDispatcher;
  private final Clock clock = Clock.systemUTC();

  public MarkerNotificationService(
      MarkerNotificationMapper markerNotificationMapper,
      IncidentAssignmentView incidentAssignmentView,
      ObjectMapper objectMapper,
      ObjectProvider<PolicePhoneMapper> policePhoneMapperProvider,
      MarkerEventPublisher markerEventPublisher,
      FcmTokenQuery fcmTokenQuery,
      FcmDispatcherPort fcmDispatcher) {
    this.markerNotificationMapper = Objects.requireNonNull(markerNotificationMapper);
    this.incidentAssignmentView = Objects.requireNonNull(incidentAssignmentView);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.policePhoneMapper =
        policePhoneMapperProvider == null ? null : policePhoneMapperProvider.getIfAvailable();
    this.markerEventPublisher = Objects.requireNonNull(markerEventPublisher);
    this.fcmTokenQuery = Objects.requireNonNull(fcmTokenQuery);
    this.fcmDispatcher = Objects.requireNonNull(fcmDispatcher);
  }

  public void publishIfNeeded(Marker marker) {
    Objects.requireNonNull(marker, "marker must not be null");
    Objects.requireNonNull(marker.getId(), "markerId must not be null");
    Objects.requireNonNull(marker.getIncidentId(), "incidentId must not be null");
    Objects.requireNonNull(marker.getOperationalPeriodId(), "opId must not be null");
    Objects.requireNonNull(marker.getPolicePhoneId(), "policePhoneId must not be null");
    Objects.requireNonNull(marker.getMarkerType(), "markerType must not be null");
    Objects.requireNonNull(marker.getLocation(), "location must not be null");
    Objects.requireNonNull(marker.getOccurredAt(), "clientTs must not be null");
    if (marker.getVersion() <= 0) {
      throw new IllegalArgumentException("markerVersion must be positive");
    }
    notificationTypeFor(MarkerType.valueOf(marker.getMarkerType()))
        .ifPresent(notificationType -> publishMarkerNotification(marker, notificationType));
  }

  private void publishMarkerNotification(Marker marker, NotificationType notificationType) {
    NotificationRecipients recipients =
        incidentAssignmentView.notificationTargets(
            marker.getIncidentId(), notificationType.getRecipientPolicy());
    UUID notificationId = UUID.randomUUID();
    Instant createdAt = clock.instant();
    MarkerNotificationPayload payload =
        createNotificationPayload(
            notificationType,
            notificationId,
            marker,
            recipients,
            MarkerNotificationStatus.SNAPSHOT_CREATED,
            INITIAL_NOTIFICATION_VERSION);
    MarkerNotification notification =
        MarkerNotification.builder()
            .id(notificationId)
            .markerId(marker.getId())
            .notificationType(notificationType)
            .recipientRule(recipients.policy())
            .recipientAccountIds(accountDbIds(recipients.accountIds()))
            .recipientPolicePhoneIds(policePhoneDbIds(recipients.policePhoneIds()))
            .notificationPayloadJson(serializeNotificationPayload(notificationType, payload))
            .status(MarkerNotificationStatus.SNAPSHOT_CREATED)
            .version(INITIAL_NOTIFICATION_VERSION)
            .createdAt(createdAt)
            .build();
    int inserted = markerNotificationMapper.insertIfAbsent(notification);
    if (inserted == 0) {
      return;
    }
    markerEventPublisher.publish(notificationType.name(), payload);
    sendFcmAfterCommit(notificationType.name(), payload);
  }

  private MarkerNotificationPayload createNotificationPayload(
      NotificationType notificationType,
      UUID notificationId,
      Marker marker,
      NotificationRecipients recipients,
      MarkerNotificationStatus status,
      long notificationVersion) {
    Objects.requireNonNull(notificationType, "notificationType must not be null");
    Objects.requireNonNull(notificationId, "notificationId must not be null");
    Objects.requireNonNull(marker, "marker must not be null");
    Objects.requireNonNull(recipients, "recipients must not be null");
    Objects.requireNonNull(status, "status must not be null");
    return MarkerNotificationPayload.builder()
        .id(notificationId)
        .markerId(marker.getId())
        .incidentId(marker.getIncidentId())
        .opId(marker.getOperationalPeriodId())
        .policePhoneId(marker.getPolicePhoneId())
        .status(status.name())
        .version(notificationVersion)
        .type(notificationType.name())
        .recipientPolicy(recipients.policy().name())
        .recipientAccountIds(recipients.accountIds())
        .recipientPolicePhoneIds(recipients.policePhoneIds())
        .markerType(marker.getMarkerType())
        .locationLabel(formatLocationLabel(marker))
        .policePhoneName(findPolicePhoneName(marker.getPolicePhoneId()))
        .clientTs(marker.getOccurredAt())
        .build();
  }

  private String serializeNotificationPayload(
      NotificationType notificationType, MarkerNotificationPayload payload) {
    Objects.requireNonNull(notificationType, "notificationType must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
    Map<String, Object> fields = payload.toMap();
    fields.put("type", notificationType.name());
    try {
      return objectMapper.writeValueAsString(fields);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("failed to serialize marker notification payload", exception);
    }
  }

  private String formatLocationLabel(Marker marker) {
    MarkerGeoJsonPoint location = MarkerGeoJsonPoint.from(marker.getLocation());
    BigDecimal lon = location.coordinates().get(0);
    BigDecimal lat = location.coordinates().get(1);
    return lon.toPlainString() + "," + lat.toPlainString();
  }

  private String findPolicePhoneName(UUID policePhoneId) {
    if (policePhoneMapper == null || policePhoneId == null) {
      return null;
    }
    return policePhoneMapper.findDisplayNameById(policePhoneId).orElse(null);
  }

  private void sendFcmAfterCommit(String eventType, MarkerNotificationPayload payload) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      sendFcm(eventType, payload);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            sendFcm(eventType, payload);
          }
        });
  }

  private void sendFcm(String eventType, MarkerNotificationPayload payload) {
    List<String> recipientTokens =
        payload.getRecipientPolicePhoneIds().stream()
            .map(MarkerNotificationService::parseUuid)
            .filter(Objects::nonNull)
            .flatMap(policePhoneId -> fcmTokenQuery.activeByPolicePhone(policePhoneId).stream())
            .map(FcmTokenRow::tokenCiphertext)
            .map(this::decryptToken)
            .filter(token -> !token.isBlank())
            .distinct()
            .toList();
    if (recipientTokens.isEmpty()) {
      return;
    }
    String eventId =
        MarkerEventIds.eventId(eventType, payload.getId(), payload.getVersion()).toString();
    try {
      fcmDispatcher.send(recipientTokens, createFcmPayload(eventType, payload), eventId);
    } catch (RuntimeException exception) {
      log.warn("failed to dispatch marker notification FCM eventId={}", eventId, exception);
    }
  }

  private static Map<String, Object> createFcmPayload(
      String eventType, MarkerNotificationPayload payload) {
    Map<String, Object> values = payload.toMap();
    // FCM은 다섯 식별자가 모두 있는 알림만 전송한다.
    for (String field : List.of("id", "markerId", "incidentId", "opId", "policePhoneId")) {
      Objects.requireNonNull(values.get(field), field + " must not be null");
    }
    values.put("type", eventType);
    values.remove("policePhoneName");
    values.remove("locationLabel", null);
    return values;
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private String decryptToken(String tokenCiphertext) {
    if (tokenCiphertext == null) {
      return "";
    }
    return tokenCiphertext.startsWith("cipher:")
        ? tokenCiphertext.substring("cipher:".length())
        : tokenCiphertext;
  }

  private Optional<NotificationType> notificationTypeFor(MarkerType markerType) {
    return switch (markerType) {
      case SUPPORT_REQUEST -> Optional.of(NotificationType.SUPPORT_REQUEST_CREATED);
      case PERSON_FOUND -> Optional.of(NotificationType.PERSON_FOUND);
      default -> Optional.empty();
    };
  }

  private static List<String> accountDbIds(List<String> accountIds) {
    return accountIds.stream()
        .map(AccountIdentityCatalog::accountIdFromCodeOrUuid)
        .map(UUID::toString)
        .toList();
  }

  private static List<String> policePhoneDbIds(List<String> policePhoneIds) {
    return policePhoneIds.stream()
        .map(MarkerNotificationService::policePhoneIdFromCodeOrUuid)
        .map(UUID::toString)
        .toList();
  }

  private static UUID policePhoneIdFromCodeOrUuid(String policePhoneCodeOrId) {
    return switch (policePhoneCodeOrId) {
      case "dev-precinct-cmd-phone-01" -> UUID.fromString("00000000-0000-0000-0000-000000000201");
      case "dev-precinct-car-01" -> UUID.fromString("50000000-0000-0000-0000-000000000001");
      case "dev-precinct-phone-01" -> UUID.fromString("00000000-0000-0000-0000-000000000101");
      case "dev-alpha-cmd-phone-01" -> UUID.fromString("00000000-0000-0000-0000-000000000204");
      case "dev-alpha-phone-01" -> UUID.fromString("00000000-0000-0000-0000-000000000205");
      case "dev-support-cmd-phone-01" -> UUID.fromString("00000000-0000-0000-0000-000000000206");
      case "dev-support-car-01" -> UUID.fromString("00000000-0000-0000-0000-000000000207");
      case "dev-support-phone-01" -> UUID.fromString("00000000-0000-0000-0000-000000000208");
      default -> UUID.fromString(policePhoneCodeOrId);
    };
  }
}
