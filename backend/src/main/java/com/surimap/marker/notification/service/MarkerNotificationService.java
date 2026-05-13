package com.surimap.marker.notification.service;

import com.surimap.account.AccountIdentityCatalog;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.dto.MarkerNotificationPublishRequestPayload;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.notification.domain.MarkerNotificationStatus;
import com.surimap.marker.notification.domain.NotificationRecipients;
import com.surimap.marker.notification.domain.NotificationType;
import com.surimap.marker.notification.repository.MarkerNotificationRecord;
import com.surimap.marker.notification.repository.MarkerNotificationRepository;
import com.surimap.marker.port.MarkerEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MarkerNotificationService {

  private static final long INITIAL_NOTIFICATION_VERSION = 1L;

  private final MarkerNotificationRepository markerNotificationRepository;
  private final NotificationRecipientResolver recipientResolver;
  private final NotificationPayloadFactory payloadFactory;
  private final MarkerEventPublisher markerEventPublisher;
  private final Clock clock;
  private final Function<MarkerNotificationContext, UUID> notificationIdFactory;

  @Autowired
  public MarkerNotificationService(
      MarkerNotificationRepository markerNotificationRepository,
      NotificationRecipientResolver recipientResolver,
      NotificationPayloadFactory payloadFactory,
      MarkerEventPublisher markerEventPublisher) {
    this(
        markerNotificationRepository,
        recipientResolver,
        payloadFactory,
        markerEventPublisher,
        Clock.systemUTC(),
        context -> UUID.randomUUID());
  }

  public MarkerNotificationService(
      MarkerNotificationRepository markerNotificationRepository,
      NotificationRecipientResolver recipientResolver,
      NotificationPayloadFactory payloadFactory,
      MarkerEventPublisher markerEventPublisher,
      Clock clock,
      Function<MarkerNotificationContext, UUID> notificationIdFactory) {
    this.markerNotificationRepository = Objects.requireNonNull(markerNotificationRepository);
    this.recipientResolver = Objects.requireNonNull(recipientResolver);
    this.payloadFactory = Objects.requireNonNull(payloadFactory);
    this.markerEventPublisher = Objects.requireNonNull(markerEventPublisher);
    this.clock = Objects.requireNonNull(clock);
    this.notificationIdFactory = Objects.requireNonNull(notificationIdFactory);
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
    UUID notificationId = notificationIdFactory.apply(context);
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
    return Optional.of(publishRequest);
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
