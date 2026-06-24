package com.surimap.marker.notification;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.service.MarkerLocationValidatorImpl;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.marker.dto.MarkerCreateRequest;
import com.surimap.marker.dto.MarkerCreateResult;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.notification.domain.NotificationRecipientPolicy;
import com.surimap.marker.notification.domain.NotificationRecipients;
import com.surimap.marker.notification.domain.NotificationType;
import com.surimap.marker.notification.fixture.NotificationFixtures;
import com.surimap.marker.notification.port.NotificationTargetPort;
import com.surimap.marker.notification.repository.MarkerNotificationRecord;
import com.surimap.marker.notification.repository.MarkerNotificationRepository;
import com.surimap.marker.notification.service.MarkerNotificationService;
import com.surimap.marker.notification.service.NotificationPayloadFactory;
import com.surimap.marker.notification.service.NotificationRecipientResolver;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.port.MarkerEventPublisher;
import com.surimap.marker.port.MarkerWriteGuardPort;
import com.surimap.marker.repository.MarkerRecord;
import com.surimap.marker.seed.support.InMemoryMarkerRepository;
import com.surimap.marker.service.MarkerCreateService;
import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S14P31C106-71 L5-T07 person found notification policy RED tests. */
@DisplayName("L5-T07 person found notification policy")
class PersonFoundNotificationPolicyRedTest {

  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550072");
  private static final UUID NOTIFICATION_ID =
      UUID.fromString("66666666-6666-6666-6666-666666660072");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110072");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222220072");
  private static final Instant CLIENT_TS = Instant.parse("2026-04-28T00:10:00Z");
  private static final Instant SERVER_TS = Instant.parse("2026-04-28T00:10:03Z");

  private InMemoryMarkerRepository markerRepository;
  private InMemoryMarkerNotificationRepository markerNotificationRepository;
  private CapturingMarkerEventPublisher eventPublisher;
  private MarkerCreateService markerCreateService;
  private MarkerRequestContext context;

  @BeforeEach
  void setUp() {
    markerRepository = new InMemoryMarkerRepository();
    markerNotificationRepository = new InMemoryMarkerNotificationRepository();
    eventPublisher = new CapturingMarkerEventPublisher();
    MarkerNotificationService markerNotificationService =
        new MarkerNotificationService(
            markerNotificationRepository,
            new NotificationRecipientResolver(new FixtureNotificationTargetPort()),
            new NotificationPayloadFactory(new ObjectMapper()),
            eventPublisher,
            Clock.fixed(SERVER_TS, ZoneOffset.UTC),
            notificationContext -> NOTIFICATION_ID);
    markerCreateService =
        new MarkerCreateService(
            markerRepository,
            new MarkerLocationValidatorImpl(),
            new MarkerOpBindingValidator(incidentId -> Optional.of(OP1_ID)),
            new AllowingMarkerWriteGuard(),
            eventPublisher,
            markerNotificationService,
            Clock.fixed(SERVER_TS, ZoneOffset.UTC),
            () -> MARKER_ID);
    context =
        new MarkerRequestContext(
            new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID), "idem-person-found-001");
  }

  @Test
  @DisplayName("PERSON_FOUND marker는 전체 배정 recipient와 PERSON_FOUND payload를 발행한다")
  void personFoundMarkerPublishesNotificationSnapshotContract() {
    MarkerCreateResult result = markerCreateService.create(personFound(), context);

    assertThat(result.response().id()).isEqualTo(MARKER_ID);
    assertThat(result.response().status()).isEqualTo(MarkerStatus.ACTIVE.name());
    assertThat(result.response().version()).isEqualTo(1L);

    MarkerRecord marker = markerRepository.records().get(0);
    assertThat(marker.getMarkerType()).isEqualTo(MarkerType.PERSON_FOUND.name());

    assertThat(eventPublisher.published())
        .extracting(MarkerPublishRequest::type)
        .containsExactly("MARKER_CREATED", "PERSON_FOUND");
    assertThat(markerNotificationRepository.recordsByMarkerId()).containsOnlyKeys(MARKER_ID);

    MarkerPublishRequest notificationEvent =
        eventPublisher.published().stream()
            .filter(event -> "PERSON_FOUND".equals(event.type()))
            .findFirst()
            .orElseThrow();

    Object payload = notificationEvent.payload();
    assertThat(payloadField(payload, "id")).isEqualTo(NOTIFICATION_ID);
    assertThat(payloadField(payload, "markerId")).isEqualTo(MARKER_ID);
    assertThat(payloadField(payload, "incidentId")).isEqualTo(INCIDENT_ID);
    assertThat(payloadField(payload, "opId")).isEqualTo(OP1_ID);
    assertThat(payloadField(payload, "policePhoneId")).isEqualTo(POLICE_PHONE_ID);
    assertThat(payloadField(payload, "status")).isEqualTo("SNAPSHOT_CREATED");
    assertThat(payloadField(payload, "version")).isEqualTo(1L);
    assertThat(payloadField(payload, "recipientPolicy"))
        .isEqualTo(NotificationFixtures.PERSON_FOUND_RECIPIENT_POLICY);
    assertThat(payloadField(payload, "recipientAccountIds"))
        .isEqualTo(NotificationFixtures.PERSON_FOUND_RECIPIENT_ACCOUNT_IDS);
    assertThat(payloadField(payload, "recipientPolicePhoneIds"))
        .isEqualTo(NotificationFixtures.PERSON_FOUND_RECIPIENT_POLICE_PHONE_IDS);
    assertThat(payloadField(payload, "markerType")).isEqualTo(MarkerType.PERSON_FOUND.name());
    assertThat(payloadField(payload, "locationLabel")).isEqualTo("126.913400,35.163100");
  }

  @Test
  @DisplayName("같은 idempotency key 재전송은 PERSON_FOUND event를 중복 발행하지 않는다")
  void duplicatePersonFoundReplayKeepsOneNotificationEvent() {
    markerCreateService.create(personFound(), context);
    markerCreateService.create(personFound(), context);

    assertThat(markerRepository.records()).hasSize(1);
    assertThat(eventPublisher.published())
        .filteredOn(event -> "PERSON_FOUND".equals(event.type()))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(payloadField(event.payload(), "id")).isEqualTo(NOTIFICATION_ID);
              assertThat(payloadField(event.payload(), "markerId")).isEqualTo(MARKER_ID);
              assertThat(payloadField(event.payload(), "status")).isEqualTo("SNAPSHOT_CREATED");
              assertThat(payloadField(event.payload(), "version")).isEqualTo(1L);
              assertThat(payloadField(event.payload(), "opId")).isEqualTo(OP1_ID);
              assertThat(payloadField(event.payload(), "policePhoneId")).isEqualTo(POLICE_PHONE_ID);
            });
  }

  private static MarkerCreateRequest personFound() {
    return new MarkerCreateRequest(
        INCIDENT_ID,
        OP1_ID,
        "PERSON_FOUND",
        new MarkerGeoJsonPoint(
            "Point", List.of(new BigDecimal("126.913400"), new BigDecimal("35.163100"))),
        null,
        "실종자 발견",
        CLIENT_TS,
        0L);
  }

  private static Object payloadField(Object payload, String name) {
    try {
      Method method = payload.getClass().getMethod(name);
      return method.invoke(payload);
    } catch (NoSuchMethodException exception) {
      return null;
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException(exception);
    } catch (InvocationTargetException exception) {
      throw new IllegalStateException(exception.getCause());
    }
  }

  private static final class CapturingMarkerEventPublisher implements MarkerEventPublisher {

    private final List<MarkerPublishRequest> published = new ArrayList<>();

    @Override
    public void publish(MarkerPublishRequest request) {
      published.add(request);
    }

    List<MarkerPublishRequest> published() {
      return published;
    }
  }

  private static final class AllowingMarkerWriteGuard implements MarkerWriteGuardPort {

    @Override
    public UUID requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context) {
      if (!"APP".equals(context.authentication().channel())) {
        throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
      }
      return null;
    }

    @Override
    public MarkerMutationContext requireUpdateAccess(UUID markerId, MarkerRequestContext context) {
      throw new UnsupportedOperationException("person found tests do not exercise update");
    }

    @Override
    public MarkerMutationContext requireDeleteAccess(UUID markerId, MarkerRequestContext context) {
      throw new UnsupportedOperationException("person found tests do not exercise delete");
    }
  }

  private static final class FixtureNotificationTargetPort implements NotificationTargetPort {

    @Override
    public NotificationRecipients notificationTargets(
        UUID incidentId, NotificationRecipientPolicy recipientPolicy) {
      assertThat(incidentId).isEqualTo(INCIDENT_ID);
      assertThat(recipientPolicy).isEqualTo(NotificationRecipientPolicy.ALL_INCIDENT_ASSIGNED);
      return new NotificationRecipients(
          recipientPolicy,
          NotificationFixtures.PERSON_FOUND_RECIPIENT_ACCOUNT_IDS,
          NotificationFixtures.PERSON_FOUND_RECIPIENT_POLICE_PHONE_IDS);
    }
  }

  private static final class InMemoryMarkerNotificationRepository
      implements MarkerNotificationRepository {

    private final Map<UUID, MarkerNotificationRecord> recordsByMarkerId = new LinkedHashMap<>();

    @Override
    public int insertIfAbsent(MarkerNotificationRecord record) {
      if (recordsByMarkerId.containsKey(record.markerId())) {
        return 0;
      }
      assertThat(record.notificationType()).isEqualTo(NotificationType.PERSON_FOUND);
      recordsByMarkerId.put(record.markerId(), record);
      return 1;
    }

    Map<UUID, MarkerNotificationRecord> recordsByMarkerId() {
      return recordsByMarkerId;
    }
  }
}
