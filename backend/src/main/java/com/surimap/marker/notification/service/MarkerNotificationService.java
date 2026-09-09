package com.surimap.marker.notification.service;

import com.surimap.account.AccountIdentityCatalog;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.dto.MarkerNotificationPublishRequestPayload;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.event.MarkerEventIds;
import com.surimap.marker.notification.domain.MarkerNotificationStatus;
import com.surimap.marker.notification.domain.NotificationRecipients;
import com.surimap.marker.notification.domain.NotificationType;
import com.surimap.marker.notification.port.FcmDispatcherPort;
import com.surimap.marker.notification.repository.MarkerNotificationRecord;
import com.surimap.marker.notification.repository.MarkerNotificationRepository;
import com.surimap.marker.port.MarkerEventPublisher;
import com.surimap.policephone.query.FcmTokenQuery;
import com.surimap.policephone.query.FcmTokenRow;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
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

  private final MarkerNotificationRepository markerNotificationRepository;
  private final NotificationRecipientResolver recipientResolver;
  private final NotificationPayloadFactory payloadFactory;
  private final MarkerEventPublisher markerEventPublisher;
  private final FcmTokenQuery fcmTokenQuery;
  private final FcmDispatcherPort fcmDispatcher;
  private final Clock clock = Clock.systemUTC();

  public MarkerNotificationService(
      MarkerNotificationRepository markerNotificationRepository,
      NotificationRecipientResolver recipientResolver,
      NotificationPayloadFactory payloadFactory,
      MarkerEventPublisher markerEventPublisher,
      FcmTokenQuery fcmTokenQuery,
      FcmDispatcherPort fcmDispatcher) {
    this.markerNotificationRepository = Objects.requireNonNull(markerNotificationRepository);
    this.recipientResolver = Objects.requireNonNull(recipientResolver);
    this.payloadFactory = Objects.requireNonNull(payloadFactory);
    this.markerEventPublisher = Objects.requireNonNull(markerEventPublisher);
    this.fcmTokenQuery = Objects.requireNonNull(fcmTokenQuery);
    this.fcmDispatcher = Objects.requireNonNull(fcmDispatcher);
  }

  public Optional<MarkerPublishRequest> publishIfNeeded(MarkerNotificationContext context) {
    Objects.requireNonNull(context, "context must not be null");
    return notificationTypeFor(context.markerType())
        .flatMap(notificationType -> publishMarkerNotification(context, notificationType));
  }

  private Optional<MarkerPublishRequest> publishMarkerNotification(
      MarkerNotificationContext context, NotificationType notificationType) {
    NotificationRecipients recipients =
        recipientResolver.resolve(context.incidentId(), notificationType);
    UUID notificationId = UUID.randomUUID();
    Instant createdAt = clock.instant();
    MarkerNotificationPublishRequestPayload payload =
        payloadFactory.markerNotificationPayload(
            notificationType,
            notificationId,
            context,
            recipients,
            MarkerNotificationStatus.SNAPSHOT_CREATED,
            INITIAL_NOTIFICATION_VERSION);
    MarkerNotificationRecord record =
        new MarkerNotificationRecord(
            notificationId,
            context.markerId(),
            notificationType,
            recipients.policy(),
            accountDbIds(recipients.accountIds()),
            policePhoneDbIds(recipients.policePhoneIds()),
            payloadFactory.toJson(notificationType, payload),
            MarkerNotificationStatus.SNAPSHOT_CREATED,
            INITIAL_NOTIFICATION_VERSION,
            createdAt);
    int inserted = markerNotificationRepository.insertIfAbsent(record);
    if (inserted == 0) {
      return Optional.empty();
    }
    MarkerPublishRequest publishRequest =
        new MarkerPublishRequest(notificationType.name(), payload);
    markerEventPublisher.publish(publishRequest);
    sendFcmAfterCommit(notificationType.name(), payload);
    return Optional.of(publishRequest);
  }

  private void sendFcmAfterCommit(
      String eventType, MarkerNotificationPublishRequestPayload payload) {
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

  private void sendFcm(String eventType, MarkerNotificationPublishRequestPayload payload) {
    List<String> recipientTokens =
        payload.recipientPolicePhoneIds().stream()
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
    String eventId = MarkerEventIds.eventId(eventType, payload.id(), payload.version()).toString();
    try {
      fcmDispatcher.send(recipientTokens, createFcmPayload(eventType, payload), eventId);
    } catch (RuntimeException exception) {
      log.warn("failed to dispatch marker notification FCM eventId={}", eventId, exception);
    }
  }

  private static Map<String, Object> createFcmPayload(
      String eventType, MarkerNotificationPublishRequestPayload payload) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("type", eventType);
    values.put("id", payload.id().toString());
    values.put("markerId", payload.markerId().toString());
    values.put("incidentId", payload.incidentId().toString());
    values.put("opId", payload.opId().toString());
    values.put("policePhoneId", payload.policePhoneId().toString());
    values.put("status", payload.status());
    values.put("version", payload.version());
    values.put("recipientPolicy", payload.recipientPolicy());
    values.put("recipientAccountIds", payload.recipientAccountIds());
    values.put("recipientPolicePhoneIds", payload.recipientPolicePhoneIds());
    values.put("markerType", payload.markerType());
    if (payload.clientTs() != null) {
      values.put("clientTs", payload.clientTs().toString());
    }
    if (payload.locationLabel() != null) {
      values.put("locationLabel", payload.locationLabel());
    }
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
