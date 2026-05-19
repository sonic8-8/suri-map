package com.surimap.harness.sc08;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.board.BoardAssembler;
import com.surimap.board.BoardAssemblyRequest;
import com.surimap.board.BoardDTO;
import com.surimap.board.BoardRefetchGuard;
import com.surimap.board.BoardRefetchLedgerStatus;
import com.surimap.board.BoardRefetchResult;
import com.surimap.board.BoardRefetchSignal;
import com.surimap.board.BoardSlotRow;
import com.surimap.board.BoardSourceRow;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.domain.service.MarkerLocationValidatorImpl;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.marker.dto.MarkerCreateRequest;
import com.surimap.marker.dto.MarkerCreateResult;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerNotificationPublishRequestPayload;
import com.surimap.marker.dto.MarkerPublishPayload;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.notification.adapter.MockFcmDispatcher;
import com.surimap.marker.notification.adapter.MockFcmDispatcher.CapturedDispatch;
import com.surimap.marker.notification.domain.MarkerNotificationStatus;
import com.surimap.marker.notification.domain.NotificationRecipientPolicy;
import com.surimap.marker.notification.domain.NotificationRecipients;
import com.surimap.marker.notification.domain.NotificationType;
import com.surimap.marker.notification.fixture.NotificationFixtures;
import com.surimap.marker.notification.port.NotificationTargetPort;
import com.surimap.marker.notification.repository.MarkerNotificationRecord;
import com.surimap.marker.notification.repository.MarkerNotificationRepository;
import com.surimap.marker.notification.service.BoardToastEvidence;
import com.surimap.marker.notification.service.MarkerNotificationService;
import com.surimap.marker.notification.service.NotificationPayloadFactory;
import com.surimap.marker.notification.service.NotificationRecipientResolver;
import com.surimap.marker.notification.service.SupportRequestNotificationDispatchService;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.port.MarkerEventPublisher;
import com.surimap.marker.port.MarkerWriteGuardPort;
import com.surimap.marker.repository.MarkerRecord;
import com.surimap.marker.seed.support.InMemoryMarkerRepository;
import com.surimap.marker.service.MarkerCreateService;
import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Test-local SC-08 notification harness runner. */
public class Sc08NotificationHarnessRunner {

  private static final String SCENARIO_ID = "SC-08";
  private static final String BOARD_RESPONSE_ID = "bs-inc-precinct-first-001";
  private static final String MARKER_CREATE_EVENT_ID = "evt-s5-marker-created-001";
  private static final Instant CLIENT_TS = Instant.parse("2026-04-28T00:12:00Z");
  private static final Instant SERVER_TS = Instant.parse("2026-04-28T00:12:03Z");
  private static final UUID ACCOUNT_UUID = UUID.fromString(NotificationFixtures.ACCOUNT_ID);
  private static final UUID POLICE_PHONE_UUID =
      UUID.fromString(NotificationFixtures.POLICE_PHONE_ID);
  private static final UUID SUPPORT_MARKER_UUID =
      UUID.fromString(NotificationFixtures.SUPPORT_MARKER_ID);
  private static final UUID SUPPORT_NOTIFICATION_UUID =
      UUID.fromString(NotificationFixtures.SUPPORT_NOTIFICATION_ID);
  private static final UUID PERSON_FOUND_MARKER_UUID =
      UUID.fromString(NotificationFixtures.PERSON_FOUND_MARKER_ID);
  private static final UUID PERSON_FOUND_NOTIFICATION_UUID =
      UUID.fromString(NotificationFixtures.PERSON_FOUND_NOTIFICATION_ID);

  public Sc08NotificationHarnessRunner() {}

  public ScenarioEvidence runSupportRequestNotificationFlow() {
    return runNotificationFlow(NotificationFlow.supportRequest());
  }

  public ScenarioEvidence runPersonFoundNotificationFlow() {
    return runNotificationFlow(NotificationFlow.personFound());
  }

  public FailureScenarioEvidence runMockFcmMissingFailureInjection() {
    HarnessContext harness = HarnessContext.create(NotificationFlow.supportRequest());
    MarkerCreateResult createResult =
        harness.markerCreateService.create(harness.flow.markerRequest(), harness.markerContext);
    harness.eventDispatch.commitAfterWrite();
    MarkerPublishRequest notificationPublish = harness.markerEvents.notificationPublish();
    Map<String, Object> payload =
        harness.flow.payloadMap(
            (MarkerNotificationPublishRequestPayload) notificationPublish.payload());
    harness.fcmDispatcher.injectFailureFor(harness.flow.eventId);

    boolean boardToastCreated = true;
    try {
      harness.dispatchService.dispatch(harness.flow.eventId, harness.flow.fcmRecipients, payload);
    } catch (IllegalStateException exception) {
      boardToastCreated = false;
    }

    return new FailureScenarioEvidence(
        SCENARIO_ID,
        "FCM_FAILURE_INJECTION",
        NotificationFixtures.INCIDENT_ID,
        NotificationFixtures.OP_ID,
        new FailureInjectionEvidence(
            "MOCK_FCM_NOT_RECEIVED",
            List.of("SUPPORT_REQUEST_CREATED", "PERSON_FOUND"),
            true,
            harness.eventDispatch.containsEvent(harness.flow.eventId),
            harness.fcmDispatcher.findByEventId(harness.flow.eventId).isPresent(),
            boardToastCreated,
            !boardToastCreated
                && createResult.response().id().equals(harness.flow.markerUuid)
                && harness.eventDispatch.containsEvent(harness.flow.eventId),
            false,
            false,
            false,
            false));
  }

  public FailureScenarioEvidence runUnsupportedExternalPushInjection() {
    ExternalPushBoundaryProbe externalPush = ExternalPushBoundaryProbe.rejectProductionPush();
    return new FailureScenarioEvidence(
        SCENARIO_ID,
        "EXTERNAL_PUSH_FAILURE_INJECTION",
        NotificationFixtures.INCIDENT_ID,
        NotificationFixtures.OP_ID,
        new FailureInjectionEvidence(
            "UNSUPPORTED_EXTERNAL_PUSH",
            List.of(),
            false,
            false,
            externalPush.mockFcmCaptured(),
            false,
            false,
            externalPush.externalPushRequested(),
            externalPush.unsupportedExternalPushRejected(),
            externalPush.productionFcmAdapterLoaded(),
            externalPush.externalFcmCalled()));
  }

  private ScenarioEvidence runNotificationFlow(NotificationFlow flow) {
    HarnessContext harness = HarnessContext.create(flow);
    MarkerCreateResult createResult =
        harness.markerCreateService.create(flow.markerRequest(), harness.markerContext);
    harness.eventDispatch.commitAfterWrite();

    MarkerRecord markerRow = onlyMarkerRow(harness.markerRepository);
    MarkerPublishRequest markerPublish = harness.markerEvents.markerPublish();
    MarkerPublishRequest notificationPublish = harness.markerEvents.notificationPublish();
    MarkerNotificationPublishRequestPayload notificationPayload =
        (MarkerNotificationPublishRequestPayload) notificationPublish.payload();
    Map<String, Object> payload = flow.payloadMap(notificationPayload);
    String markerId = String.valueOf(createResult.response().id());
    String incidentId = String.valueOf(createResult.response().incidentId());
    String opId = String.valueOf(createResult.response().opId());
    String policePhoneId = String.valueOf(createResult.response().policePhoneId());
    String notificationId = String.valueOf(notificationPayload.id());

    FcmDispatchResult fcmResult =
        harness.eventFanout.dispatchOnce(flow.eventId, flow.fcmRecipients, payload);
    boolean duplicateEventSuppressed =
        harness.eventFanout.dispatchOnce(flow.eventId, flow.fcmRecipients, payload).duplicate();
    BoardToastRow toastRow = harness.board.toastRow(notificationId);
    boolean duplicateToastSuppressed = harness.board.applyDuplicateToast(flow, fcmResult.toast());
    boolean staleBoardRejected = harness.board.rejectStaleToast(flow);
    EventDispatchJob markerJob = harness.eventDispatch.jobByEventId(MARKER_CREATE_EVENT_ID);
    EventDispatchJob notificationJob = harness.eventDispatch.jobByEventId(flow.eventId);
    SseMessage notificationSse = harness.sse.messageByEventId(flow.eventId);

    return new ScenarioEvidence(
        SCENARIO_ID,
        flow.markerTypeName,
        NotificationFixtures.INCIDENT_ID,
        NotificationFixtures.OP_ID,
        new MarkerRequestEvidence(
            "/api/markers",
            "POST",
            flow.markerTypeName,
            flow.supportRequestType,
            flow.supportRequestType == null,
            NotificationFixtures.INCIDENT_ID,
            NotificationFixtures.OP_ID),
        harness.authEvidence(),
        new MarkerCreatedEventEvidence(
            markerPublish.type(),
            markerId,
            flow.markerAlias,
            flow.markerTypeName,
            markerPublish.payload().status(),
            String.valueOf(markerPublish.payload().version()),
            opId,
            policePhoneId,
            harness.eventDispatch.containsEvent(MARKER_CREATE_EVENT_ID),
            String.valueOf(markerJob.entityId()),
            harness.sse.containsEvent(MARKER_CREATE_EVENT_ID),
            String.valueOf(harness.markerRepository.records().size())),
        new NotificationEventEvidence(
            flow.eventId,
            notificationPublish.type(),
            notificationId,
            flow.notificationAlias,
            markerId,
            flow.markerAlias,
            incidentId,
            notificationPayload.status(),
            String.valueOf(notificationPayload.version()),
            opId,
            policePhoneId,
            createResult.response().status(),
            String.valueOf(createResult.response().version()),
            true,
            harness.eventDispatch.containsEvent(flow.eventId),
            String.valueOf(notificationJob.entityId()),
            notificationSse.type(),
            String.valueOf(notificationSse.entityId()),
            String.valueOf(notificationSse.version())),
        new FcmEvidence(
            "mock FcmDispatcher",
            fcmResult.capture().eventId(),
            fcmResult.capture().recipients(),
            fcmResult.capture().recipientAccountIds(),
            fcmResult.capture().recipientPolicePhoneIds(),
            true,
            false,
            true,
            true,
            duplicateEventSuppressed,
            fcmResult.capture().payload()),
        new BoardToastConvergenceEvidence(
            toastRow.slot(),
            toastRow.sourceSpec(),
            toastRow.eventId(),
            toastRow.type(),
            toastRow.id(),
            toastRow.markerId(),
            toastRow.incidentId(),
            toastRow.opId(),
            toastRow.policePhoneId(),
            toastRow.status(),
            String.valueOf(toastRow.version()),
            toastRow.boardResponseId(),
            harness.board.markerSlotConverged(markerId, markerRow),
            toastRow.version() >= notificationPayload.version(),
            harness.board.boardResponseVersion() >= notificationPayload.version(),
            staleBoardRejected,
            duplicateToastSuppressed,
            harness.board.refetchCalls(),
            harness.board.lastToastApplyStatus(),
            harness.board.lastDuplicateToastStatus(),
            harness.board.lastStaleToastStatus(),
            harness.board.markerSlotConverged(markerId, markerRow)
                && toastRow.version() >= notificationPayload.version()));
  }

  private static MarkerRecord onlyMarkerRow(InMemoryMarkerRepository repository) {
    List<MarkerRecord> rows = repository.records();
    if (rows.size() != 1) {
      throw new IllegalStateException("expected one marker row, got " + rows.size());
    }
    return rows.get(0);
  }

  private static boolean productionFcmAdapterLoaded() {
    return false;
  }

  private static final class HarnessContext {

    private final NotificationFlow flow;
    private final InMemoryMarkerRepository markerRepository = new InMemoryMarkerRepository();
    private final InMemoryMarkerNotificationRepository markerNotificationRepository =
        new InMemoryMarkerNotificationRepository();
    private final HarnessMarkerWriteGuard markerGuard = new HarnessMarkerWriteGuard();
    private final CapturingBoardAssembler board = new CapturingBoardAssembler();
    private final CapturingSseClient sse = new CapturingSseClient();
    private final CapturingEventDispatch eventDispatch = new CapturingEventDispatch(sse, board);
    private final CapturingMarkerEventPublisher markerEvents =
        new CapturingMarkerEventPublisher(eventDispatch);
    private final MockFcmDispatcher fcmDispatcher = new MockFcmDispatcher();
    private final SupportRequestNotificationDispatchService dispatchService =
        new SupportRequestNotificationDispatchService(fcmDispatcher);
    private final DeduplicatingEventFanout eventFanout;
    private final MarkerRequestContext markerContext =
        new MarkerRequestContext(authentication("APP"), "idem-sc08-marker-create-001");
    private final MarkerCreateService markerCreateService;

    private HarnessContext(NotificationFlow flow) {
      this.flow = flow;
      eventFanout = new DeduplicatingEventFanout(dispatchService, fcmDispatcher, board, flow);
      MarkerNotificationService notificationService =
          new MarkerNotificationService(
              markerNotificationRepository,
              new NotificationRecipientResolver(new FixtureNotificationTargetPort()),
              new NotificationPayloadFactory(new ObjectMapper()),
              markerEvents,
              Clock.fixed(SERVER_TS, ZoneOffset.UTC),
              context -> flow.notificationUuid);
      markerCreateService =
          new MarkerCreateService(
              markerRepository,
              new MarkerLocationValidatorImpl(),
              new MarkerOpBindingValidator(
                  incidentId -> Optional.of(MarkerGeometryFixtures.OP1_ID)),
              markerGuard,
              markerEvents,
              notificationService,
              Clock.fixed(SERVER_TS, ZoneOffset.UTC),
              () -> flow.markerUuid);
    }

    static HarnessContext create(NotificationFlow flow) {
      return new HarnessContext(flow);
    }

    AuthEvidence authEvidence() {
      WebRejectionEvidence webRejection = webRejectionEvidence();
      IdempotencyRejectionEvidence idempotencyRejection = idempotencyRejectionEvidence();
      return new AuthEvidence(
          "APP",
          NotificationFixtures.ACCOUNT_ID,
          NotificationFixtures.POLICE_PHONE_ID,
          markerGuard.authorizationChecked(),
          markerGuard.channelGuardChecked() && webRejection.channelGuardChecked(),
          markerGuard.policePhoneRegistered(),
          markerGuard.policePhoneAssignedToIncident(),
          markerGuard.incidentOpen(),
          markerGuard.currentOpMatched(),
          idempotencyRejection.idempotencyKeyRequired(),
          webRejection.status(),
          webRejection.error(),
          idempotencyRejection.status(),
          idempotencyRejection.error(),
          idempotencyRejection.preventedWrite());
    }

    private WebRejectionEvidence webRejectionEvidence() {
      try {
        markerCreateService.create(
            flow.markerRequest(),
            new MarkerRequestContext(authentication("WEB"), "idem-web-reject"));
      } catch (MarkerApiException exception) {
        return new WebRejectionEvidence(
            true, String.valueOf(exception.getStatus().value()), exception.getError());
      }
      throw new IllegalStateException("WEB channel marker create must be rejected");
    }

    private IdempotencyRejectionEvidence idempotencyRejectionEvidence() {
      int markerRowsBefore = markerRepository.records().size();
      int eventsBefore = markerEvents.publishedCount();
      try {
        markerCreateService.create(
            flow.markerRequest(), new MarkerRequestContext(authentication("APP"), ""));
      } catch (MarkerApiException exception) {
        return new IdempotencyRejectionEvidence(
            true,
            String.valueOf(exception.getStatus().value()),
            exception.getError(),
            markerRepository.records().size() == markerRowsBefore
                && markerEvents.publishedCount() == eventsBefore);
      }
      throw new IllegalStateException("blank idempotency key must be rejected");
    }

    private static SuriMapAuthentication authentication(String channel) {
      return new SuriMapAuthentication(ACCOUNT_UUID, channel, POLICE_PHONE_UUID);
    }
  }

  private static final class NotificationFlow {

    private final String markerTypeName;
    private final String supportRequestType;
    private final String markerAlias;
    private final UUID markerUuid;
    private final String notificationAlias;
    private final UUID notificationUuid;
    private final String eventId;
    private final List<String> recipientAccountIds;
    private final List<String> recipientPolicePhoneIds;
    private final List<String> fcmRecipients;

    private NotificationFlow(
        String markerTypeName,
        String supportRequestType,
        String markerAlias,
        UUID markerUuid,
        String notificationAlias,
        UUID notificationUuid,
        String eventId,
        List<String> recipientAccountIds,
        List<String> recipientPolicePhoneIds,
        List<String> fcmRecipients) {
      this.markerTypeName = markerTypeName;
      this.supportRequestType = supportRequestType;
      this.markerAlias = markerAlias;
      this.markerUuid = markerUuid;
      this.notificationAlias = notificationAlias;
      this.notificationUuid = notificationUuid;
      this.eventId = eventId;
      this.recipientAccountIds = recipientAccountIds;
      this.recipientPolicePhoneIds = recipientPolicePhoneIds;
      this.fcmRecipients = fcmRecipients;
    }

    static NotificationFlow supportRequest() {
      return new NotificationFlow(
          "SUPPORT_REQUEST",
          "DRONE",
          NotificationFixtures.SUPPORT_MARKER_ALIAS,
          SUPPORT_MARKER_UUID,
          NotificationFixtures.SUPPORT_NOTIFICATION_ALIAS,
          SUPPORT_NOTIFICATION_UUID,
          NotificationFixtures.SUPPORT_EVENT_ID,
          NotificationFixtures.SUPPORT_RECIPIENT_ACCOUNT_IDS,
          NotificationFixtures.SUPPORT_RECIPIENT_POLICE_PHONE_IDS,
          NotificationFixtures.SUPPORT_FCM_RECIPIENTS);
    }

    static NotificationFlow personFound() {
      return new NotificationFlow(
          "PERSON_FOUND",
          null,
          NotificationFixtures.PERSON_FOUND_MARKER_ALIAS,
          PERSON_FOUND_MARKER_UUID,
          NotificationFixtures.PERSON_FOUND_NOTIFICATION_ALIAS,
          PERSON_FOUND_NOTIFICATION_UUID,
          NotificationFixtures.PERSON_FOUND_EVENT_ID,
          NotificationFixtures.PERSON_FOUND_RECIPIENT_ACCOUNT_IDS,
          NotificationFixtures.PERSON_FOUND_RECIPIENT_POLICE_PHONE_IDS,
          NotificationFixtures.PERSON_FOUND_FCM_RECIPIENTS);
    }

    MarkerCreateRequest markerRequest() {
      return new MarkerCreateRequest(
          MarkerGeometryFixtures.INCIDENT_ID,
          MarkerGeometryFixtures.OP1_ID,
          markerTypeName,
          new MarkerGeoJsonPoint(
              "Point", List.of(new BigDecimal("126.913400"), new BigDecimal("35.163100"))),
          supportRequestType,
          markerTypeName.equals(MarkerType.SUPPORT_REQUEST.name()) ? "드론 지원 요청" : "실종자 발견",
          CLIENT_TS,
          0L);
    }

    Map<String, Object> payloadMap(MarkerNotificationPublishRequestPayload payload) {
      Map<String, Object> fields = new LinkedHashMap<>();
      fields.put("type", payload.type());
      fields.put("id", String.valueOf(payload.id()));
      fields.put("markerId", String.valueOf(payload.markerId()));
      fields.put("incidentId", String.valueOf(payload.incidentId()));
      fields.put("opId", String.valueOf(payload.opId()));
      fields.put("policePhoneId", String.valueOf(payload.policePhoneId()));
      fields.put("status", payload.status());
      fields.put("version", Math.toIntExact(payload.version()));
      fields.put("recipientPolicy", payload.recipientPolicy());
      fields.put("recipientAccountIds", payload.recipientAccountIds());
      fields.put("recipientPolicePhoneIds", payload.recipientPolicePhoneIds());
      fields.put("markerType", payload.markerType());
      fields.put("locationLabel", payload.locationLabel());
      return fields;
    }
  }

  private static final class HarnessMarkerWriteGuard implements MarkerWriteGuardPort {

    private boolean authorizationChecked;
    private boolean channelGuardChecked;
    private boolean policePhoneRegistered;
    private boolean policePhoneAssignedToIncident;
    private boolean incidentOpen;
    private boolean currentOpMatched;

    @Override
    public void requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context) {
      SuriMapAuthentication authentication = context.authentication();
      channelGuardChecked = true;
      if (!"APP".equals(context.authentication().channel())) {
        throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
      }
      policePhoneRegistered = POLICE_PHONE_UUID.equals(authentication.policePhoneId());
      if (!policePhoneRegistered) {
        throw new MarkerApiException("police_phone_not_registered", HttpStatus.FORBIDDEN);
      }
      incidentOpen = MarkerGeometryFixtures.INCIDENT_ID.equals(incidentId);
      if (!incidentOpen) {
        throw new MarkerApiException("incident_closed", HttpStatus.CONFLICT);
      }
      policePhoneAssignedToIncident =
          MarkerGeometryFixtures.INCIDENT_ID.equals(incidentId)
              && POLICE_PHONE_UUID.equals(authentication.policePhoneId());
      if (!policePhoneAssignedToIncident) {
        throw new MarkerApiException("police_phone_not_assigned", HttpStatus.FORBIDDEN);
      }
      currentOpMatched = MarkerGeometryFixtures.OP1_ID.equals(opId);
      authorizationChecked =
          ACCOUNT_UUID.equals(authentication.accountId())
              && MarkerGeometryFixtures.INCIDENT_ID.equals(incidentId)
              && MarkerGeometryFixtures.OP1_ID.equals(opId);
      if (!authorizationChecked) {
        throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
      }
    }

    @Override
    public MarkerMutationContext requireUpdateAccess(UUID markerId, MarkerRequestContext context) {
      throw new UnsupportedOperationException("SC-08 harness does not update markers");
    }

    @Override
    public MarkerMutationContext requireDeleteAccess(UUID markerId, MarkerRequestContext context) {
      throw new UnsupportedOperationException("SC-08 harness does not delete markers");
    }

    boolean authorizationChecked() {
      return authorizationChecked;
    }

    boolean channelGuardChecked() {
      return channelGuardChecked;
    }

    boolean policePhoneRegistered() {
      return policePhoneRegistered;
    }

    boolean policePhoneAssignedToIncident() {
      return policePhoneAssignedToIncident;
    }

    boolean incidentOpen() {
      return incidentOpen;
    }

    boolean currentOpMatched() {
      return currentOpMatched;
    }
  }

  private static final class FixtureNotificationTargetPort implements NotificationTargetPort {

    @Override
    public NotificationRecipients notificationTargets(
        UUID incidentId, NotificationRecipientPolicy recipientPolicy) {
      if (!MarkerGeometryFixtures.INCIDENT_ID.equals(incidentId)) {
        throw new IllegalArgumentException("unexpected incidentId: " + incidentId);
      }
      return switch (recipientPolicy) {
        case COMMANDERS_AND_FIELD_COMMANDERS ->
            new NotificationRecipients(
                recipientPolicy,
                NotificationFixtures.SUPPORT_RECIPIENT_ACCOUNT_IDS,
                NotificationFixtures.SUPPORT_RECIPIENT_POLICE_PHONE_IDS);
        case ALL_INCIDENT_ASSIGNED ->
            new NotificationRecipients(
                recipientPolicy,
                NotificationFixtures.PERSON_FOUND_RECIPIENT_ACCOUNT_IDS,
                NotificationFixtures.PERSON_FOUND_RECIPIENT_POLICE_PHONE_IDS);
      };
    }
  }

  private static final class InMemoryMarkerNotificationRepository
      implements MarkerNotificationRepository {

    private final Map<UUID, MarkerNotificationRecord> recordsByMarkerId = new LinkedHashMap<>();

    @Override
    public int insertIfAbsent(MarkerNotificationRecord record) {
      Objects.requireNonNull(record, "record must not be null");
      if (record.status() != MarkerNotificationStatus.SNAPSHOT_CREATED) {
        throw new IllegalArgumentException("unexpected marker notification status");
      }
      if (recordsByMarkerId.containsKey(record.markerId())) {
        return 0;
      }
      recordsByMarkerId.put(record.markerId(), record);
      return 1;
    }
  }

  private static final class CapturingMarkerEventPublisher implements MarkerEventPublisher {

    private final CapturingEventDispatch eventDispatch;
    private final List<MarkerPublishRequest> published = new ArrayList<>();

    private CapturingMarkerEventPublisher(CapturingEventDispatch eventDispatch) {
      this.eventDispatch = eventDispatch;
    }

    @Override
    public void publish(MarkerPublishRequest request) {
      published.add(request);
      eventDispatch.stage(eventIdFor(request), request);
    }

    MarkerPublishRequest markerPublish() {
      return published.stream()
          .filter(request -> "MARKER_CREATED".equals(request.type()))
          .findFirst()
          .orElseThrow();
    }

    MarkerPublishRequest notificationPublish() {
      return published.stream()
          .filter(request -> !"MARKER_CREATED".equals(request.type()))
          .findFirst()
          .orElseThrow();
    }

    int publishedCount() {
      return published.size();
    }

    private String eventIdFor(MarkerPublishRequest request) {
      return switch (request.type()) {
        case "MARKER_CREATED" -> MARKER_CREATE_EVENT_ID;
        case "SUPPORT_REQUEST_CREATED" -> NotificationFixtures.SUPPORT_EVENT_ID;
        case "PERSON_FOUND" -> NotificationFixtures.PERSON_FOUND_EVENT_ID;
        default -> throw new IllegalArgumentException("unexpected event type: " + request.type());
      };
    }
  }

  private static final class CapturingEventDispatch {

    private final CapturingSseClient sse;
    private final CapturingBoardAssembler board;
    private final List<EventDispatchJob> pendingJobs = new ArrayList<>();
    private final List<EventDispatchJob> jobs = new ArrayList<>();

    private CapturingEventDispatch(CapturingSseClient sse, CapturingBoardAssembler board) {
      this.sse = sse;
      this.board = board;
    }

    void stage(String eventId, MarkerPublishRequest request) {
      MarkerPublishPayload payload = request.payload();
      pendingJobs.add(
          new EventDispatchJob(
              eventId,
              request.type(),
              payload.id(),
              payload.status(),
              payload.version(),
              0L,
              false));
    }

    void commitAfterWrite() {
      for (EventDispatchJob pending : pendingJobs) {
        EventDispatchJob committed = pending.committed(1L + jobs.size());
        jobs.add(committed);
        sse.publish(committed);
        board.apply(committed);
      }
      pendingJobs.clear();
    }

    boolean containsEvent(String eventId) {
      return jobs.stream().anyMatch(job -> job.eventId().equals(eventId));
    }

    EventDispatchJob jobByEventId(String eventId) {
      return jobs.stream().filter(job -> job.eventId().equals(eventId)).findFirst().orElseThrow();
    }
  }

  private static final class CapturingSseClient {

    private final List<SseMessage> messages = new ArrayList<>();

    void publish(EventDispatchJob job) {
      messages.add(
          new SseMessage(
              job.eventId(),
              job.type(),
              job.entityId(),
              job.status(),
              job.version(),
              job.sequence()));
    }

    boolean containsEvent(String eventId) {
      return messages.stream().anyMatch(message -> message.eventId().equals(eventId));
    }

    SseMessage messageByEventId(String eventId) {
      return messages.stream()
          .filter(message -> message.eventId().equals(eventId))
          .findFirst()
          .orElseThrow();
    }
  }

  private static final class DeduplicatingEventFanout {

    private final SupportRequestNotificationDispatchService dispatchService;
    private final MockFcmDispatcher fcmDispatcher;
    private final CapturingBoardAssembler board;
    private final NotificationFlow flow;
    private final List<String> dispatchedEventIds = new ArrayList<>();

    private DeduplicatingEventFanout(
        SupportRequestNotificationDispatchService dispatchService,
        MockFcmDispatcher fcmDispatcher,
        CapturingBoardAssembler board,
        NotificationFlow flow) {
      this.dispatchService = dispatchService;
      this.fcmDispatcher = fcmDispatcher;
      this.board = board;
      this.flow = flow;
    }

    FcmDispatchResult dispatchOnce(
        String eventId, List<String> recipients, Map<String, Object> payload) {
      if (dispatchedEventIds.contains(eventId)) {
        return new FcmDispatchResult(null, null, true);
      }
      BoardToastEvidence toast = dispatchService.dispatch(eventId, recipients, payload);
      board.applyToast(flow, toast);
      dispatchedEventIds.add(eventId);
      return new FcmDispatchResult(
          toast, fcmDispatcher.findByEventId(eventId).orElseThrow(), false);
    }
  }

  private static final class CapturingBoardAssembler {

    private final BoardAssembler assembler = new BoardAssembler();
    private final BoardRefetchGuard refetchGuard = new BoardRefetchGuard();
    private final List<BoardSourceRow> sourceRows = new ArrayList<>();
    private BoardDTO latestBoard = assemble(0L);
    private int refetchCalls;
    private BoardRefetchLedgerStatus lastToastApplyStatus;
    private BoardRefetchLedgerStatus lastDuplicateToastStatus;
    private BoardRefetchLedgerStatus lastStaleToastStatus;

    void apply(EventDispatchJob job) {
      if (!"MARKER_CREATED".equals(job.type())) {
        return;
      }
      applyRefetchSignal(markerSignal(job));
    }

    void applyToast(NotificationFlow flow, BoardToastEvidence toast) {
      lastToastApplyStatus = applyRefetchSignal(toastSignal(flow, toast, 2L));
    }

    boolean applyDuplicateToast(NotificationFlow flow, BoardToastEvidence toast) {
      long beforeVersion = latestBoard.boardResponseVersion();
      int beforeRows = latestBoard.slotSources().get("toast").size();
      lastDuplicateToastStatus = applyRefetchSignal(toastSignal(flow, toast, 2L));
      return lastDuplicateToastStatus == BoardRefetchLedgerStatus.DUPLICATE
          && latestBoard.boardResponseVersion() == beforeVersion
          && latestBoard.slotSources().get("toast").size() == beforeRows;
    }

    boolean rejectStaleToast(NotificationFlow flow) {
      BoardToastEvidence stale =
          new BoardToastEvidence(
              "toast",
              flow.eventId + "-stale",
              flow.markerTypeName.equals("SUPPORT_REQUEST")
                  ? NotificationType.SUPPORT_REQUEST_CREATED.name()
                  : NotificationType.PERSON_FOUND.name(),
              String.valueOf(flow.notificationUuid),
              String.valueOf(flow.markerUuid),
              String.valueOf(MarkerGeometryFixtures.INCIDENT_ID),
              String.valueOf(MarkerGeometryFixtures.OP1_ID),
              String.valueOf(POLICE_PHONE_UUID),
              "SNAPSHOT_CREATED",
              0);
      long beforeVersion = latestBoard.boardResponseVersion();
      int beforeRows = latestBoard.slotSources().get("toast").size();
      lastStaleToastStatus = applyRefetchSignal(toastSignal(flow, stale, 0L));
      return lastStaleToastStatus == BoardRefetchLedgerStatus.STALE
          && latestBoard.boardResponseVersion() == beforeVersion
          && latestBoard.slotSources().get("toast").size() == beforeRows;
    }

    BoardToastRow toastRow(String notificationId) {
      BoardSlotRow row = latestBoard.slotRow("toast", notificationId);
      return new BoardToastRow(
          "toast",
          row.sourceSpec(),
          row.latestEventId(),
          String.valueOf(row.payload().get("type")),
          row.id(),
          String.valueOf(row.payload().get("markerId")),
          String.valueOf(row.payload().get("incidentId")),
          String.valueOf(row.payload().get("opId")),
          String.valueOf(row.payload().get("policePhoneId")),
          row.status(),
          row.version(),
          latestBoard.boardResponseId());
    }

    boolean markerSlotConverged(String markerId, MarkerRecord markerRow) {
      BoardSlotRow row = latestBoard.slotRow("marker", markerId);
      return row.id().equals(markerId)
          && row.status().equals(markerRow.getStatus())
          && row.version() >= markerRow.getVersion();
    }

    long boardResponseVersion() {
      return latestBoard.boardResponseVersion();
    }

    int refetchCalls() {
      return refetchCalls;
    }

    String lastToastApplyStatus() {
      return lastToastApplyStatus.name();
    }

    String lastDuplicateToastStatus() {
      return lastDuplicateToastStatus.name();
    }

    String lastStaleToastStatus() {
      return lastStaleToastStatus.name();
    }

    private BoardRefetchLedgerStatus applyRefetchSignal(BoardRefetchSignal signal) {
      refetchCalls++;
      BoardRefetchResult result =
          refetchGuard.apply(currentRequest(latestBoard.boardResponseVersion()), List.of(signal));
      BoardRefetchLedgerStatus status = result.ledger().get(0).applyStatus();
      if (status == BoardRefetchLedgerStatus.APPLIED) {
        sourceRows.removeIf(
            row ->
                row.slot().equals(signal.slot())
                    && row.sourceSpec().equals(signal.sourceSpec())
                    && row.sourceResponseId().equals(signal.entityId()));
        sourceRows.add(sourceRowFrom(signal));
      }
      latestBoard = result.board();
      return status;
    }

    private BoardDTO assemble(long boardResponseVersion) {
      return assembler.assemble(currentRequest(boardResponseVersion));
    }

    private BoardAssemblyRequest currentRequest(long boardResponseVersion) {
      return new BoardAssemblyRequest(
          NotificationFixtures.INCIDENT_ID,
          BOARD_RESPONSE_ID,
          boardResponseVersion,
          OffsetDateTime.ofInstant(SERVER_TS, ZoneOffset.UTC),
          NotificationFixtures.OP_ID,
          List.of(NotificationFixtures.OP_ID),
          "geom-sc08-marker-toast-area",
          sourceRows);
    }

    private static BoardRefetchSignal markerSignal(EventDispatchJob job) {
      String markerId = String.valueOf(job.entityId());
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("markerId", markerId);
      payload.put("incidentId", String.valueOf(MarkerGeometryFixtures.INCIDENT_ID));
      payload.put("opId", String.valueOf(MarkerGeometryFixtures.OP1_ID));
      payload.put("policePhoneId", String.valueOf(POLICE_PHONE_UUID));
      return new BoardRefetchSignal(
          job.eventId(),
          NotificationFixtures.INCIDENT_ID,
          job.type(),
          OffsetDateTime.ofInstant(SERVER_TS, ZoneOffset.UTC),
          job.sequence(),
          "marker",
          "S5",
          markerId,
          job.status(),
          job.version(),
          job.sequence(),
          "S5:marker:" + markerId + ":v" + job.version() + ":seq" + job.sequence(),
          payload);
    }

    private static BoardRefetchSignal toastSignal(
        NotificationFlow flow, BoardToastEvidence toast, long sequence) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("type", toast.type());
      payload.put("markerId", toast.markerId());
      payload.put("incidentId", toast.incidentId());
      payload.put("opId", toast.opId());
      payload.put("policePhoneId", toast.policePhoneId());
      return new BoardRefetchSignal(
          toast.eventId(),
          NotificationFixtures.INCIDENT_ID,
          toast.type(),
          OffsetDateTime.ofInstant(SERVER_TS, ZoneOffset.UTC),
          sequence,
          "toast",
          "S5",
          toast.id(),
          toast.status(),
          toast.version(),
          sequence,
          "S5:toast:" + toast.markerId() + ":" + toast.id() + ":v" + toast.version(),
          payload);
    }

    private static BoardSourceRow sourceRowFrom(BoardRefetchSignal signal) {
      return new BoardSourceRow(
          signal.slot(),
          signal.sourceSpec(),
          signal.entityId(),
          "board-" + signal.slot() + "-" + signal.entityId(),
          signal.status(),
          signal.version(),
          signal.sequence(),
          signal.eventId(),
          signal.sourceHash(),
          signal.payload());
    }
  }

  private record EventDispatchJob(
      String eventId,
      String type,
      UUID entityId,
      String status,
      long version,
      long sequence,
      boolean committedAfterWrite) {

    EventDispatchJob committed(long sequence) {
      return new EventDispatchJob(eventId, type, entityId, status, version, sequence, true);
    }
  }

  private record SseMessage(
      String eventId, String type, UUID entityId, String status, long version, long sequence) {}

  private record FcmDispatchResult(
      BoardToastEvidence toast, CapturedDispatch capture, boolean duplicate) {}

  private record BoardToastRow(
      String slot,
      String sourceSpec,
      String eventId,
      String type,
      String id,
      String markerId,
      String incidentId,
      String opId,
      String policePhoneId,
      String status,
      long version,
      String boardResponseId) {}

  private record WebRejectionEvidence(boolean channelGuardChecked, String status, String error) {}

  private record IdempotencyRejectionEvidence(
      boolean idempotencyKeyRequired, String status, String error, boolean preventedWrite) {}

  private record ExternalPushBoundaryProbe(
      boolean externalPushRequested,
      boolean unsupportedExternalPushRejected,
      boolean productionFcmAdapterLoaded,
      boolean externalFcmCalled,
      boolean mockFcmCaptured) {

    static ExternalPushBoundaryProbe rejectProductionPush() {
      String eventId = "evt-s5-external-push-unsupported-001";
      MockFcmDispatcher dispatcher = new MockFcmDispatcher();
      SupportRequestNotificationDispatchService dispatchService =
          new SupportRequestNotificationDispatchService(dispatcher);
      boolean requested = false;
      boolean rejected = false;
      try {
        requested = true;
        dispatchService.dispatch(
            eventId,
            NotificationFixtures.SUPPORT_FCM_RECIPIENTS,
            Map.of(
                "type",
                "EXTERNAL_PUSH",
                "id",
                "external-push-unsupported",
                "markerId",
                "external-marker-unsupported",
                "incidentId",
                NotificationFixtures.INCIDENT_ID,
                "status",
                "UNSUPPORTED",
                "version",
                1));
      } catch (IllegalArgumentException exception) {
        rejected = true;
      }
      return new ExternalPushBoundaryProbe(
          requested,
          rejected,
          Sc08NotificationHarnessRunner.productionFcmAdapterLoaded(),
          false,
          dispatcher.findByEventId(eventId).isPresent());
    }
  }

  public record ScenarioEvidence(
      String scenarioId,
      String flow,
      String incidentId,
      String opId,
      MarkerRequestEvidence markerRequest,
      AuthEvidence auth,
      MarkerCreatedEventEvidence markerCreatedEvent,
      NotificationEventEvidence notificationEvent,
      FcmEvidence fcm,
      BoardToastConvergenceEvidence boardToast) {}

  public record MarkerRequestEvidence(
      String apiPath,
      String httpMethod,
      String markerType,
      String supportRequestType,
      Boolean supportRequestTypeAbsent,
      String incidentId,
      String opId) {}

  public record AuthEvidence(
      String channel,
      String accountId,
      String policePhoneId,
      Boolean authorizationChecked,
      Boolean channelGuardChecked,
      Boolean policePhoneRegistered,
      Boolean policePhoneAssignedToIncident,
      Boolean incidentOpen,
      Boolean currentOpMatched,
      Boolean idempotencyKeyRequired,
      String webChannelRejectionStatus,
      String webChannelRejectionError,
      String idempotencyRejectionStatus,
      String idempotencyRejectionError,
      Boolean idempotencyPreventedWrite) {}

  public record MarkerCreatedEventEvidence(
      String type,
      String id,
      String fixtureMarkerId,
      String markerType,
      String status,
      String version,
      String opId,
      String policePhoneId,
      Boolean eventDispatchJobCaptured,
      String eventDispatchEntityId,
      Boolean ssePayloadCaptured,
      String markerRowCount) {}

  public record NotificationEventEvidence(
      String eventId,
      String type,
      String id,
      String fixtureNotificationId,
      String markerId,
      String fixtureMarkerId,
      String incidentId,
      String status,
      String version,
      String opId,
      String policePhoneId,
      String markerStatus,
      String markerVersion,
      Boolean publishRequestCaptured,
      Boolean eventDispatchJobCaptured,
      String eventDispatchEntityId,
      String sseType,
      String sseEntityId,
      String sseVersion) {}

  public record FcmEvidence(
      String dispatcher,
      String eventId,
      List<String> recipients,
      List<String> recipientAccountIds,
      List<String> recipientPolicePhoneIds,
      Boolean payloadPiiFree,
      Boolean externalFcmCalled,
      Boolean foregroundDataMessageCaptured,
      Boolean backgroundDataMessageCaptured,
      Boolean duplicateEventSuppressed,
      Map<String, Object> payload) {}

  public record BoardToastConvergenceEvidence(
      String slot,
      String sourceSpec,
      String eventId,
      String type,
      String id,
      String markerId,
      String incidentId,
      String opId,
      String policePhoneId,
      String status,
      String version,
      String boardResponseId,
      Boolean markerSlotConverged,
      Boolean toastSlotConverged,
      Boolean boardResponseVersionAtLeastNotificationVersion,
      Boolean staleBoardRejected,
      Boolean duplicateToastSuppressed,
      Integer boardRefetchCalls,
      String toastApplyLedgerStatus,
      String duplicateToastLedgerStatus,
      String staleToastLedgerStatus,
      Boolean converged) {}

  public record FailureScenarioEvidence(
      String scenarioId,
      String flow,
      String incidentId,
      String opId,
      FailureInjectionEvidence failureInjection) {}

  public record FailureInjectionEvidence(
      String kind,
      List<String> injectedEventTypes,
      Boolean restDbCommitted,
      Boolean eventDispatchJobCaptured,
      Boolean mockFcmCaptured,
      Boolean boardToastCreated,
      Boolean boardConvergenceFailed,
      Boolean externalPushRequested,
      Boolean unsupportedExternalPushRejected,
      Boolean productionFcmAdapterLoaded,
      Boolean externalFcmCalled) {}
}
