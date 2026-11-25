package com.surimap.app.service.marker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.account.AccountIdentityCatalog;
import com.surimap.client.fcm.FcmDispatcherPort;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerNotification;
import com.surimap.domain.marker.MarkerNotificationMapper;
import com.surimap.domain.marker.MarkerNotificationStatus;
import com.surimap.domain.marker.MarkerNotificationType;
import com.surimap.domain.marker.MarkerType;
import com.surimap.global.event.MarkerEventIds;
import com.surimap.global.event.MarkerEventPublisher;
import com.surimap.global.event.MarkerNotificationPayload;
import com.surimap.global.geometry.GeoJsonPoint;
import com.surimap.incident.service.IncidentAssignmentView;
import com.surimap.incident.service.IncidentAssignmentView.NotificationTargets;
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
  private final MarkerEventPublisher markerEventPublisher;
  private final FcmTokenQuery fcmTokenQuery;
  private final FcmDispatcherPort fcmDispatcher;
  private final Clock clock = Clock.systemUTC();

  public MarkerNotificationService(
      MarkerNotificationMapper markerNotificationMapper,
      IncidentAssignmentView incidentAssignmentView,
      ObjectMapper objectMapper,
      MarkerEventPublisher markerEventPublisher,
      FcmTokenQuery fcmTokenQuery,
      FcmDispatcherPort fcmDispatcher) {
    this.markerNotificationMapper = Objects.requireNonNull(markerNotificationMapper);
    this.incidentAssignmentView = Objects.requireNonNull(incidentAssignmentView);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.markerEventPublisher = Objects.requireNonNull(markerEventPublisher);
    this.fcmTokenQuery = Objects.requireNonNull(fcmTokenQuery);
    this.fcmDispatcher = Objects.requireNonNull(fcmDispatcher);
  }

  public void publishIfNeeded(Marker marker) {
    Objects.requireNonNull(marker, "marker must not be null");
    Objects.requireNonNull(marker.getId(), "markerId must not be null");
    Objects.requireNonNull(marker.getIncidentId(), "incidentId must not be null");
    Objects.requireNonNull(marker.getOperationalPeriodId(), "opId must not be null");
    Objects.requireNonNull(marker.getMarkerType(), "markerType must not be null");
    Objects.requireNonNull(marker.getLocation(), "location must not be null");
    Objects.requireNonNull(marker.getOccurredAt(), "clientTs must not be null");
    if (marker.getVersion() <= 0) {
      throw new IllegalArgumentException("markerVersion must be positive");
    }
    notificationTypeFor(MarkerType.valueOf(marker.getMarkerType()))
        .ifPresent(notificationType -> publishMarkerNotification(marker, notificationType));
  }

  private void publishMarkerNotification(Marker marker, MarkerNotificationType notificationType) {
    NotificationTargets recipients =
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
            .recipientRule(notificationType.getRecipientPolicy())
            .recipientAccountIds(accountDbIds(recipients.accountIds()))
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
      MarkerNotificationType notificationType,
      UUID notificationId,
      Marker marker,
      NotificationTargets recipients,
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
        .status(status.name())
        .version(notificationVersion)
        .type(notificationType.name())
        .recipientPolicy(notificationType.getRecipientPolicy().name())
        .recipientAccountIds(recipients.accountIds())
        .markerType(marker.getMarkerType())
        .locationLabel(formatLocationLabel(marker))
        .createdByAccountDisplayName(
            incidentAssignmentView
                .accountDisplayNames(marker.getIncidentId())
                .get(marker.getCreatedByAccountId()))
        .clientTs(marker.getOccurredAt())
        .build();
  }

  private String serializeNotificationPayload(
      MarkerNotificationType notificationType, MarkerNotificationPayload payload) {
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
    GeoJsonPoint location = GeoJsonPoint.from(marker.getLocation());
    BigDecimal lon = location.getCoordinates().get(0);
    BigDecimal lat = location.getCoordinates().get(1);
    return lon.toPlainString() + "," + lat.toPlainString();
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
    List<UUID> recipientAccountIds =
        payload.getRecipientAccountIds().stream()
            .map(AccountIdentityCatalog::accountIdFromCodeOrUuid)
            .toList();
    if (recipientAccountIds.isEmpty()) {
      return;
    }
    List<String> recipientTokens =
        fcmTokenQuery.activeByAccounts(recipientAccountIds).stream()
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
      FcmDispatcherPort.DispatchResult dispatchResult =
          fcmDispatcher.send(recipientTokens, createFcmPayload(eventType, payload), eventId);
      if (!dispatchResult.isFullySuccessful()) {
        log.warn(
            "failed to dispatch marker notification FCM eventId={} successCount={} failureCount={}",
            eventId,
            dispatchResult.successCount(),
            dispatchResult.failureCount());
      }
    } catch (RuntimeException exception) {
      log.warn("failed to dispatch marker notification FCM eventId={}", eventId, exception);
    }
  }

  private static Map<String, Object> createFcmPayload(
      String eventType, MarkerNotificationPayload payload) {
    Map<String, Object> values = payload.toMap();
    // 네 식별자가 모두 있는 알림만 FCM으로 보낸다.
    for (String field : List.of("id", "markerId", "incidentId", "opId")) {
      Objects.requireNonNull(values.get(field), field + " must not be null");
    }
    values.put("type", eventType);
    values.remove("createdByAccountDisplayName");
    values.remove("locationLabel", null);
    return values;
  }

  private String decryptToken(String tokenCiphertext) {
    if (tokenCiphertext == null) {
      return "";
    }
    return tokenCiphertext.startsWith("cipher:")
        ? tokenCiphertext.substring("cipher:".length())
        : tokenCiphertext;
  }

  private Optional<MarkerNotificationType> notificationTypeFor(MarkerType markerType) {
    return switch (markerType) {
      case SUPPORT_REQUEST -> Optional.of(MarkerNotificationType.SUPPORT_REQUEST_CREATED);
      case PERSON_FOUND -> Optional.of(MarkerNotificationType.PERSON_FOUND);
      default -> Optional.empty();
    };
  }

  private static List<String> accountDbIds(List<String> accountIds) {
    return accountIds.stream()
        .map(AccountIdentityCatalog::accountIdFromCodeOrUuid)
        .map(UUID::toString)
        .toList();
  }
}
