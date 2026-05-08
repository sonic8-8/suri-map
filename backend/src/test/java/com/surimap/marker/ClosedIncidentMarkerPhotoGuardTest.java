package com.surimap.marker;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.maparea.testdouble.SearchAreaQueryMock;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.service.MarkerLocationValidatorImpl;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.marker.dto.MarkerCreateRequest;
import com.surimap.marker.dto.MarkerDeleteRequest;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.dto.MarkerUpdateRequest;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.notification.domain.NotificationRecipientPolicy;
import com.surimap.marker.notification.domain.NotificationRecipients;
import com.surimap.marker.notification.port.NotificationTargetPort;
import com.surimap.marker.notification.repository.MarkerNotificationRecord;
import com.surimap.marker.notification.repository.MarkerNotificationRepository;
import com.surimap.marker.notification.service.MarkerNotificationService;
import com.surimap.marker.notification.service.NotificationPayloadFactory;
import com.surimap.marker.notification.service.NotificationRecipientResolver;
import com.surimap.marker.photo.adapter.MockObjectStorageAdapter;
import com.surimap.marker.photo.domain.PhotoMarkerContext;
import com.surimap.marker.photo.domain.PhotoStatus;
import com.surimap.marker.photo.dto.PhotoAttachRequest;
import com.surimap.marker.photo.dto.PhotoUploadUrlRequest;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.port.PhotoEventPublisher;
import com.surimap.marker.photo.port.PhotoWriteGuardPort;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.service.PhotoRequestContext;
import com.surimap.marker.photo.service.PhotoService;
import com.surimap.marker.photo.support.InMemoryPhotoRepository;
import com.surimap.marker.port.MarkerEventPublisher;
import com.surimap.marker.port.MarkerWriteGuardPort;
import com.surimap.marker.purge.MarkerPhotoPurgeHook;
import com.surimap.marker.purge.MarkerPhotoPurgeHookAdapter;
import com.surimap.marker.repository.MarkerCreateRecord;
import com.surimap.marker.repository.MarkerRecord;
import com.surimap.marker.seed.support.InMemoryMarkerRepository;
import com.surimap.marker.service.MarkerCreateService;
import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.marker.service.MarkerUpdateDeleteService;
import com.surimap.retention.purge.PurgeHookName;
import com.surimap.retention.purge.PurgeHookRequest;
import com.surimap.retention.purge.PurgeHookResult;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S14P31C106-148 L5-T04C closed incident marker/photo guard RED tests. */
@DisplayName("L5-T04C closed incident marker/photo contract")
class ClosedIncidentMarkerPhotoGuardTest {

  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555551148");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111148");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222221148");
  private static final UUID PURGE_RUN_ID =
      UUID.fromString("33333333-3333-3333-3333-333333331148");
  private static final Instant CLIENT_TS = Instant.parse("2026-05-08T01:00:00Z");
  private static final Instant SERVER_TS = Instant.parse("2026-05-08T01:00:03Z");
  private static final Instant CLOSED_AT = Instant.parse("2026-05-08T01:05:00Z");
  private static final Instant PURGE_DEADLINE_TS = CLOSED_AT.plus(Duration.ofHours(24));

  private InMemoryMarkerRepository markerRepository;
  private CapturingMarkerEventPublisher markerEventPublisher;
  private ClosedMarkerWriteGuard closedMarkerGuard;
  private MarkerRequestContext markerContext;

  @BeforeEach
  void setUp() {
    markerRepository = new InMemoryMarkerRepository();
    markerEventPublisher = new CapturingMarkerEventPublisher();
    closedMarkerGuard = new ClosedMarkerWriteGuard();
    markerContext =
        new MarkerRequestContext(
            new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID),
            "idem-l5-t04c-closed-marker");
  }

  @Nested
  @DisplayName("closed marker writes")
  class ClosedMarkerWrites {

    @Test
    @DisplayName("POST /markers는 incident_closed이고 marker row와 PublishRequest를 만들지 않는다")
    void createClosedIncidentRejectedWithoutMarkerRowOrPublishRequest() {
      MarkerCreateService service =
          new MarkerCreateService(
              markerRepository,
              new MarkerLocationValidatorImpl(new SearchAreaQueryMock()),
              new MarkerOpBindingValidator(incidentId -> Optional.of(OP1_ID)),
              closedMarkerGuard,
              markerEventPublisher,
              Clock.fixed(SERVER_TS, ZoneOffset.UTC),
              () -> MARKER_ID);

      assertIncidentClosed(
          () -> service.create(defaultCreateRequest(), markerContext), MarkerApiException.class);
      assertThat(markerRepository.records()).isEmpty();
      assertThat(markerEventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("SUPPORT_REQUEST 생성도 incident_closed이고 marker_notification과 PublishRequest를 만들지 않는다")
    void supportRequestClosedIncidentRejectedBeforeMarkerNotificationSideEffect() {
      CapturingMarkerNotificationRepository markerNotificationRepository =
          new CapturingMarkerNotificationRepository();
      MarkerNotificationService markerNotificationService =
          new MarkerNotificationService(
              markerNotificationRepository,
              new NotificationRecipientResolver(new StaticNotificationTargetPort()),
              new NotificationPayloadFactory(new ObjectMapper()),
              markerEventPublisher,
              Clock.fixed(SERVER_TS, ZoneOffset.UTC),
              context -> UUID.fromString("66666666-6666-6666-6666-666666661148"));
      MarkerCreateService service =
          new MarkerCreateService(
              markerRepository,
              new MarkerLocationValidatorImpl(new SearchAreaQueryMock()),
              new MarkerOpBindingValidator(incidentId -> Optional.of(OP1_ID)),
              closedMarkerGuard,
              markerEventPublisher,
              markerNotificationService,
              Clock.fixed(SERVER_TS, ZoneOffset.UTC),
              () -> MARKER_ID);

      assertIncidentClosed(
          () -> service.create(supportRequestCreateRequest(), markerContext),
          MarkerApiException.class);

      assertThat(markerRepository.records()).isEmpty();
      assertThat(markerNotificationRepository.records()).isEmpty();
      assertThat(markerEventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("PATCH /markers/{markerId}는 incident_closed이고 기존 row와 PublishRequest를 변경하지 않는다")
    void updateClosedIncidentRejectedWithoutMarkerMutationOrPublishRequest() {
      seedActiveMarker();
      MarkerUpdateDeleteService service = updateDeleteService();

      assertIncidentClosed(
          () ->
              service.update(
                  MARKER_ID,
                  new MarkerUpdateRequest(1L, null, "post-close edit", "NOTE"),
                  markerContext),
          MarkerApiException.class);

      MarkerRecord row = markerRepository.findById(MARKER_ID).orElseThrow();
      assertThat(row.getStatus()).isEqualTo(MarkerStatus.ACTIVE.name());
      assertThat(row.getVersion()).isEqualTo(1L);
      assertThat(row.getMemo()).isEqualTo("pre-close marker");
      assertThat(markerEventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("DELETE /markers/{markerId}는 incident_closed이고 기존 row와 PublishRequest를 변경하지 않는다")
    void deleteClosedIncidentRejectedWithoutMarkerMutationOrPublishRequest() {
      seedActiveMarker();
      MarkerUpdateDeleteService service = updateDeleteService();

      assertIncidentClosed(
          () -> service.delete(MARKER_ID, new MarkerDeleteRequest(1L, "closed"), markerContext),
          MarkerApiException.class);

      MarkerRecord row = markerRepository.findById(MARKER_ID).orElseThrow();
      assertThat(row.getStatus()).isEqualTo(MarkerStatus.ACTIVE.name());
      assertThat(row.getVersion()).isEqualTo(1L);
      assertThat(markerEventPublisher.published()).isEmpty();
    }
  }

  @Nested
  @DisplayName("closed photo writes")
  class ClosedPhotoWrites {

    private MockObjectStorageAdapter storage;
    private InMemoryPhotoRepository photoRepository;
    private SwitchingPhotoWriteGuard photoGuard;
    private CapturingPhotoEventPublisher photoEventPublisher;
    private PhotoService photoService;
    private PhotoRequestContext photoContext;

    @BeforeEach
    void setUp() {
      storage = new MockObjectStorageAdapter();
      photoRepository = new InMemoryPhotoRepository();
      photoGuard = new SwitchingPhotoWriteGuard();
      photoGuard.allow(
          new PhotoMarkerContext(INCIDENT_ID, MARKER_ID, OP1_ID, POLICE_PHONE_ID, "UPDATED", 1L));
      photoEventPublisher = new CapturingPhotoEventPublisher();
      photoService =
          new PhotoService(
              storage,
              photoRepository,
              photoGuard,
              photoEventPublisher,
              Clock.fixed(SERVER_TS, ZoneOffset.UTC));
      photoContext =
          new PhotoRequestContext(
              new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID),
              "idem-l5-t04c-closed-photo");
    }

    @Test
    @DisplayName("사진 upload-url은 incident_closed이고 photo row와 PublishRequest를 만들지 않는다")
    void uploadUrlClosedIncidentRejectedWithoutPhotoRowOrPublishRequest() {
      photoGuard.closeIncident();

      assertIncidentClosed(
          () ->
              photoService.createUploadUrl(
                  MARKER_ID, new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null), photoContext),
          PhotoApiException.class);

      assertThat(photoRepository.countByMarkerIdAndStatusIn(MARKER_ID, PhotoStatus.countedStatuses()))
          .isZero();
      assertThat(photoEventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("사진 attach는 incident_closed이고 기존 pending row와 PublishRequest를 변경하지 않는다")
    void attachClosedIncidentRejectedWithoutPhotoMutationOrPublishRequest() {
      var upload =
          photoService.createUploadUrl(
              MARKER_ID, new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null), photoContext);
      storage.simulateUpload(photoRepository.findById(upload.photoId()).orElseThrow().objectKey());
      photoGuard.closeIncident();

      assertIncidentClosed(
          () ->
              photoService.attach(
                  MARKER_ID,
                  upload.photoId(),
                  new PhotoAttachRequest(1_048_576L, "image/jpeg", null, null, null),
                  photoContext),
          PhotoApiException.class);

      assertThat(photoRepository.findById(upload.photoId()))
          .get()
          .extracting("status", "version")
          .containsExactly(PhotoStatus.PENDING_UPLOAD, 1L);
      assertThat(photoEventPublisher.published()).isEmpty();
    }
  }

  @Test
  @DisplayName("S1-3 boundary can call MarkerPhotoPurgeHook idempotently")
  void markerPhotoPurgeHookContractExistsAndIsCallableByS13Boundary() throws Exception {
    Class<?> hookType = markerPhotoPurgeHookType();

    assertThat(hookType.isInterface()).isTrue();
    Method purgeMethod =
        hookType.getMethod(
            "purgeIncidentMarkerPhotos", UUID.class, UUID.class, Instant.class, Instant.class);
    assertThat(purgeMethod.getReturnType()).isEqualTo(PurgeHookResult.class);

    PurgeHookResult result = PurgeHookResult.succeeded(2, 0);
    Object hook =
        Proxy.newProxyInstance(
            hookType.getClassLoader(),
            new Class<?>[] {hookType},
            (proxy, method, args) -> {
              if (method.equals(purgeMethod)) {
                return result;
              }
              if ("toString".equals(method.getName())) {
                return "marker-photo-purge-hook-test-double";
              }
              throw new UnsupportedOperationException(method.getName());
            });

    assertThat(invokePurge(purgeMethod, hook)).isEqualTo(result);
    assertThat(invokePurge(purgeMethod, hook)).isEqualTo(result);
  }

  @Test
  @DisplayName("MarkerPhotoPurgeHookAdapter는 MARKER_PHOTO 이름과 요청 필드를 그대로 위임한다")
  void markerPhotoPurgeHookAdapterDelegatesExactPurgeRequestFields() {
    CapturingMarkerPhotoPurgeHook delegate =
        new CapturingMarkerPhotoPurgeHook(PurgeHookResult.succeeded(2, 0));
    MarkerPhotoPurgeHookAdapter adapter = new MarkerPhotoPurgeHookAdapter(delegate);
    PurgeHookRequest request =
        new PurgeHookRequest(INCIDENT_ID, PURGE_RUN_ID, CLOSED_AT, PURGE_DEADLINE_TS);

    PurgeHookResult first = adapter.purge(request);
    PurgeHookResult second = adapter.purge(request);

    assertThat(adapter.name()).isEqualTo(PurgeHookName.MARKER_PHOTO);
    assertThat(second).isEqualTo(first);
    assertThat(delegate.calls())
        .containsExactly(
            new MarkerPhotoPurgeCall(INCIDENT_ID, PURGE_RUN_ID, CLOSED_AT, PURGE_DEADLINE_TS),
            new MarkerPhotoPurgeCall(INCIDENT_ID, PURGE_RUN_ID, CLOSED_AT, PURGE_DEADLINE_TS));
  }

  private MarkerUpdateDeleteService updateDeleteService() {
    return new MarkerUpdateDeleteService(
        markerRepository,
        new MarkerLocationValidatorImpl(new SearchAreaQueryMock()),
        closedMarkerGuard,
        markerEventPublisher,
        Clock.fixed(SERVER_TS, ZoneOffset.UTC));
  }

  private void seedActiveMarker() {
    markerRepository.insertCreate(
        new MarkerCreateRecord(
            INCIDENT_ID,
            MARKER_ID,
            OP1_ID,
            null,
            MarkerType.CLUE,
            null,
            new MarkerGeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.956500"), new BigDecimal("37.571200")))
                .toPoint(),
            "pre-close marker",
            CLIENT_TS,
            ACCOUNT_ID,
            POLICE_PHONE_ID,
            MarkerSource.APP,
            MarkerStatus.ACTIVE,
            1L));
  }

  private static MarkerCreateRequest defaultCreateRequest() {
    return new MarkerCreateRequest(
        INCIDENT_ID,
        OP1_ID,
        "CLUE",
        new MarkerGeoJsonPoint(
            "Point", List.of(new BigDecimal("126.956500"), new BigDecimal("37.571200"))),
        null,
        "post-close marker",
        CLIENT_TS,
        0L);
  }

  private static MarkerCreateRequest supportRequestCreateRequest() {
    return new MarkerCreateRequest(
        INCIDENT_ID,
        OP1_ID,
        "SUPPORT_REQUEST",
        new MarkerGeoJsonPoint(
            "Point", List.of(new BigDecimal("126.956500"), new BigDecimal("37.571200"))),
        "DRONE",
        "post-close support request",
        CLIENT_TS,
        0L);
  }

  private static Class<?> markerPhotoPurgeHookType() {
    try {
      return Class.forName("com.surimap.marker.purge.MarkerPhotoPurgeHook");
    } catch (ClassNotFoundException exception) {
      fail(
          "Expected S5 MarkerPhotoPurgeHook contract type "
              + "com.surimap.marker.purge.MarkerPhotoPurgeHook",
          exception);
      throw new AssertionError(exception);
    }
  }

  private static PurgeHookResult invokePurge(Method purgeMethod, Object hook) throws Exception {
    try {
      return (PurgeHookResult)
          purgeMethod.invoke(hook, INCIDENT_ID, PURGE_RUN_ID, CLOSED_AT, PURGE_DEADLINE_TS);
    } catch (InvocationTargetException exception) {
      throw new AssertionError(exception.getTargetException());
    }
  }

  private static <T extends RuntimeException> void assertIncidentClosed(
      ThrowingRunnable action, Class<T> exceptionType) {
    assertThatThrownBy(action::run)
        .isInstanceOf(exceptionType)
        .extracting("error", "status")
        .containsExactly("incident_closed", HttpStatus.CONFLICT);
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run();
  }

  private static final class ClosedMarkerWriteGuard implements MarkerWriteGuardPort {

    @Override
    public void requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context) {
      throw closed();
    }

    @Override
    public MarkerMutationContext requireUpdateAccess(UUID markerId, MarkerRequestContext context) {
      throw closed();
    }

    @Override
    public MarkerMutationContext requireDeleteAccess(UUID markerId, MarkerRequestContext context) {
      throw closed();
    }

    private MarkerApiException closed() {
      return new MarkerApiException("incident_closed", HttpStatus.CONFLICT);
    }
  }

  private static final class SwitchingPhotoWriteGuard implements PhotoWriteGuardPort {

    private PhotoMarkerContext context;
    private boolean closed;

    void allow(PhotoMarkerContext context) {
      this.context = context;
    }

    void closeIncident() {
      closed = true;
    }

    @Override
    public PhotoMarkerContext requireUploadUrlAccess(UUID markerId, PhotoRequestContext context) {
      return requireOpen(markerId);
    }

    @Override
    public PhotoMarkerContext requireAttachAccess(
        UUID markerId, UUID photoId, PhotoRequestContext context) {
      return requireOpen(markerId);
    }

    private PhotoMarkerContext requireOpen(UUID markerId) {
      if (closed) {
        throw new PhotoApiException("incident_closed", HttpStatus.CONFLICT);
      }
      if (context == null || !context.markerId().equals(markerId)) {
        throw new PhotoApiException("write_conflict", HttpStatus.CONFLICT);
      }
      return context;
    }
  }

  private static final class CapturingMarkerNotificationRepository
      implements MarkerNotificationRepository {

    private final List<MarkerNotificationRecord> records = new ArrayList<>();

    @Override
    public int insertIfAbsent(MarkerNotificationRecord record) {
      records.add(record);
      return 1;
    }

    List<MarkerNotificationRecord> records() {
      return records;
    }
  }

  private static final class StaticNotificationTargetPort implements NotificationTargetPort {

    @Override
    public NotificationRecipients notificationTargets(
        UUID incidentId, NotificationRecipientPolicy recipientPolicy) {
      return new NotificationRecipients(
          recipientPolicy, List.of("acct-cmd-alpha"), List.of("fcm:dev-alpha-phone-01"));
    }
  }

  private static final class CapturingMarkerPhotoPurgeHook implements MarkerPhotoPurgeHook {

    private final PurgeHookResult result;
    private final List<MarkerPhotoPurgeCall> calls = new ArrayList<>();

    private CapturingMarkerPhotoPurgeHook(PurgeHookResult result) {
      this.result = result;
    }

    @Override
    public PurgeHookResult purgeIncidentMarkerPhotos(
        UUID incidentId, UUID purgeRunId, Instant closedAt, Instant purgeDeadlineTs) {
      calls.add(new MarkerPhotoPurgeCall(incidentId, purgeRunId, closedAt, purgeDeadlineTs));
      return result;
    }

    List<MarkerPhotoPurgeCall> calls() {
      return calls;
    }
  }

  private record MarkerPhotoPurgeCall(
      UUID incidentId, UUID purgeRunId, Instant closedAt, Instant purgeDeadlineTs) {}

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

  private static final class CapturingPhotoEventPublisher implements PhotoEventPublisher {

    private final List<com.surimap.marker.photo.dto.PublishRequest> published = new ArrayList<>();

    @Override
    public void publish(com.surimap.marker.photo.dto.PublishRequest request) {
      published.add(request);
    }

    List<com.surimap.marker.photo.dto.PublishRequest> published() {
      return published;
    }
  }
}
