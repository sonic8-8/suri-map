package com.surimap.harness.sc06;

import com.surimap.board.BoardAssembler;
import com.surimap.board.BoardAssemblyRequest;
import com.surimap.board.BoardDTO;
import com.surimap.board.BoardSlotRow;
import com.surimap.board.BoardSourceRow;
import com.surimap.marker.domain.exception.InvalidGeometryException;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.domain.service.MarkerLocationValidatorImpl;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.marker.dto.MarkerCreateRequest;
import com.surimap.marker.dto.MarkerCreateResult;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.photo.adapter.MockObjectStorageAdapter;
import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoMarkerContext;
import com.surimap.marker.photo.domain.PhotoStatus;
import com.surimap.marker.photo.dto.PhotoAttachRequest;
import com.surimap.marker.photo.dto.PhotoAttachResult;
import com.surimap.marker.photo.dto.PhotoUploadUrlRequest;
import com.surimap.marker.photo.dto.PhotoUploadUrlResponse;
import com.surimap.marker.photo.dto.PublishRequest;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.fixture.PhotoFixtures;
import com.surimap.marker.photo.port.PhotoEventPublisher;
import com.surimap.marker.photo.port.PhotoWriteGuardPort;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.service.PhotoRequestContext;
import com.surimap.marker.photo.service.PhotoService;
import com.surimap.marker.photo.support.InMemoryPhotoRepository;
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

/** Test-local SC-06 marker/photo harness runner. */
public class Sc06MarkerPhotoHarnessRunner {

  private static final String SCENARIO_ID = "SC-06";
  private static final String INCIDENT_ALIAS = MarkerGeometryFixtures.INCIDENT_ALIAS;
  private static final String INCIDENT_ID = String.valueOf(MarkerGeometryFixtures.INCIDENT_ID);
  private static final UUID INCIDENT_UUID = MarkerGeometryFixtures.INCIDENT_ID;
  private static final String OP_ALIAS = MarkerGeometryFixtures.OP1_ALIAS;
  private static final String OP_ID = String.valueOf(MarkerGeometryFixtures.OP1_ID);
  private static final UUID OP_UUID = MarkerGeometryFixtures.OP1_ID;
  private static final String ACCOUNT_CODE = MarkerGeometryFixtures.ACCOUNT_ALIAS;
  private static final String ACCOUNT_ID = "11111111-1111-1111-1111-111111110003";
  private static final UUID ACCOUNT_UUID = UUID.fromString(ACCOUNT_ID);
  private static final String POLICE_PHONE_CODE = MarkerGeometryFixtures.POLICE_PHONE_ALIAS;
  private static final String POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000101";
  private static final UUID POLICE_PHONE_UUID = UUID.fromString(POLICE_PHONE_ID);
  private static final String MARKER_ALIAS = MarkerGeometryFixtures.MARKER_ALIAS;
  private static final String MARKER_ID = PhotoFixtures.HARNESS_MARKER_ID;
  private static final UUID MARKER_UUID = PhotoFixtures.MARKER_ID;
  private static final String PHOTO_ALIAS = PhotoFixtures.HARNESS_PHOTO_ALIAS;
  private static final String PHOTO_ID = PhotoFixtures.HARNESS_PHOTO_ID;
  private static final String MARKER_CREATE_EVENT_ID = "evt-s5-marker-created-001";
  private static final String PHOTO_ATTACH_EVENT_ID = "evt-s5-marker-updated-photo-001";
  private static final Instant CLIENT_TS = Instant.parse("2026-04-28T00:05:00Z");
  private static final Instant SERVER_TS = Instant.parse("2026-04-28T00:05:03Z");

  public Sc06MarkerPhotoHarnessRunner() {}

  public ScenarioEvidence runOnlineMarkerWithOneAttachedPhoto() {
    HarnessContext harness = HarnessContext.create();
    MarkerGeoJsonPoint point =
        new MarkerGeoJsonPoint(
            "Point", List.of(new BigDecimal("126.913400"), new BigDecimal("35.163100")));

    MarkerCreateResult markerCreate =
        harness.markerCreateService.create(markerCreateRequest(point), harness.markerContext);
    harness.eventDispatch.commitAfterWrite();
    MarkerRecord markerRow = onlyMarkerRow(harness.markerRepository);
    MarkerPublishRequest markerPublish = harness.markerEvents.only();

    harness.photoGuard.allow(
        new PhotoMarkerContext(
            markerCreate.response().incidentId(),
            markerCreate.response().id(),
            markerCreate.response().opId(),
            markerCreate.response().policePhoneId(),
            "UPDATED",
            markerCreate.response().version()));

    PhotoUploadUrlResponse upload =
        harness.photoService.createUploadUrl(
            markerCreate.response().id(),
            new PhotoUploadUrlRequest(
                PhotoFixtures.JPEG_CONTENT_TYPE,
                PhotoFixtures.FIXTURE_ONE_MB_BYTES,
                PhotoFixtures.CHECKSUM_SHA256),
            harness.photoContext);
    MarkerPhoto pendingPhoto = harness.photoRepository.findById(upload.photoId()).orElseThrow();
    harness.storage.simulateUpload(pendingPhoto.objectKey());

    PhotoAttachResult photoAttach =
        harness.photoService.attach(
            markerCreate.response().id(),
            upload.photoId(),
            new PhotoAttachRequest(
                PhotoFixtures.FIXTURE_ONE_MB_BYTES,
                PhotoFixtures.JPEG_CONTENT_TYPE,
                640,
                480,
                PhotoFixtures.CHECKSUM_SHA256),
            harness.photoContext);
    harness.eventDispatch.commitAfterWrite();
    PublishRequest photoPublish = harness.photoEvents.only();
    MarkerPhoto attachedPhoto = harness.photoRepository.findById(upload.photoId()).orElseThrow();
    EventDispatchJob photoJob = harness.eventDispatch.jobByEventId(PHOTO_ATTACH_EVENT_ID);
    SseMessage sseMessage = harness.sse.messageByEventId(PHOTO_ATTACH_EVENT_ID);
    BoardMarkerRow boardRow = harness.board.rowByMarkerId(MARKER_ID);
    boolean staleRefetchRejected = harness.board.rejectStaleMarkerRefetch(attachedPhoto);

    return new ScenarioEvidence(
        SCENARIO_ID,
        INCIDENT_ID,
        MARKER_ID,
        PHOTO_ID,
        harness.markerGuard.authEvidence(),
        new PointGeometryEvidence(
            point.type(),
            markerPublish.payload().location().coordinates().stream()
                .map(BigDecimal::toPlainString)
                .toList(),
            "EPSG:4326",
            markerRow.getLocation().getSRID() == 4326),
        harness.currentOpEvidence(markerCreate.response().opId()),
        new StorageEvidence(
            PhotoFixtures.MOCK_OBJECT_STORAGE_URI,
            upload.uploadUrl(),
            pendingPhoto.objectKey(),
            String.valueOf(upload.photoId()),
            false,
            harness
                .photoRepository
                .findById(upload.photoId())
                .filter(photo -> photo.status() == PhotoStatus.ATTACHED)
                .isPresent()),
        new MarkerEventEvidence(
            markerPublish.type(),
            markerPublish.payload().status(),
            String.valueOf(markerPublish.payload().version()),
            OP_ID,
            POLICE_PHONE_ID,
            harness.markerEvents.published().size(),
            harness.eventDispatch.containsEvent(MARKER_CREATE_EVENT_ID),
            markerRow.getStatus(),
            harness.markerRepository.records().size()),
        new PhotoEventEvidence(
            PHOTO_ATTACH_EVENT_ID,
            photoPublish.type(),
            photoPublish.payload().status(),
            String.valueOf(photoPublish.payload().version()),
            PHOTO_ID,
            String.valueOf(photoPublish.payload().photoDelta().photoId()),
            photoPublish.payload().photoDelta().status(),
            String.valueOf(photoPublish.payload().photoDelta().version()),
            photoAttach.response().status(),
            String.valueOf(photoAttach.response().version()),
            harness.photoEvents.published().size(),
            harness.photoRepository.countByMarkerIdAndStatusIn(
                markerCreate.response().id(), PhotoStatus.countedStatuses())),
        new EventDispatchJobEvidence(
            photoJob.eventId(),
            MARKER_ID,
            photoJob.status(),
            String.valueOf(photoJob.version()),
            photoJob.type(),
            photoJob.photoDeltaStatus(),
            harness.eventDispatch.jobs().size(),
            photoJob.committedAfterWrite()),
        new SseEvidence(
            sseMessage.eventId(),
            MARKER_ID,
            String.valueOf(sseMessage.version()),
            sseMessage.status(),
            harness.sse.messages().size()),
        new BoardEvidence(
            "marker",
            boardRow.latestEventId(),
            MARKER_ID,
            PHOTO_ID,
            String.valueOf(boardRow.internalPhotoId()),
            boardRow.photoStatus(),
            String.valueOf(boardRow.photoVersion()),
            harness.board.rows().size(),
            boardRow.boardResponseId(),
            String.valueOf(boardRow.boardResponseVersion()),
            boardRow.sourceSpec(),
            boardRow.sourceHash(),
            boardRow.slotSourceCount(),
            boardRow.sourceVersionsCount(),
            boardRow.sourceHashesCount(),
            boardRow.refetchCalls(),
            staleRefetchRejected,
            Objects.equals(
                    boardRow.internalPhotoId(), photoPublish.payload().photoDelta().photoId())
                && boardRow.photoVersion() >= photoAttach.response().version()
                && boardRow.markerVersion() >= photoAttach.response().markerVersion()));
  }

  public InvalidGeometryEvidence runInvalidGeometryRejection() {
    HarnessContext harness = HarnessContext.create();
    int markerRowsBefore = harness.markerRepository.records().size();
    long photoRowsBefore =
        harness.photoRepository.countByMarkerIdAndStatusIn(
            MARKER_UUID, PhotoStatus.countedStatuses());
    int eventJobsBefore = harness.eventDispatch.jobs().size();
    int boardRowsBefore = harness.board.rows().size();

    String error = "unexpected_acceptance";
    String httpStatus = "200";
    try {
      harness.markerCreateService.create(
          markerCreateRequest(
              new MarkerGeoJsonPoint(
                  "Point", List.of(new BigDecimal("35.163100"), new BigDecimal("126.913400")))),
          harness.markerContext);
    } catch (InvalidGeometryException exception) {
      error = exception.errorCode();
      httpStatus = "400";
    } catch (MarkerApiException exception) {
      error = exception.getError();
      httpStatus = String.valueOf(exception.getStatus().value());
    }

    int markerRowsAfter = harness.markerRepository.records().size();
    long photoRowsAfter =
        harness.photoRepository.countByMarkerIdAndStatusIn(
            MARKER_UUID, PhotoStatus.countedStatuses());
    int eventJobsAfter = harness.eventDispatch.jobs().size();
    int boardRowsAfter = harness.board.rows().size();

    return new InvalidGeometryEvidence(
        new InvalidPointGeometryEvidence("coord-latlon-swapped", "[35.163100,126.913400]"),
        new RejectionEvidence(
            httpStatus,
            error,
            markerRowsBefore,
            markerRowsAfter,
            photoRowsBefore,
            photoRowsAfter,
            eventJobsBefore,
            eventJobsAfter,
            boardRowsBefore,
            boardRowsAfter,
            markerRowsAfter > markerRowsBefore,
            photoRowsAfter > photoRowsBefore,
            eventJobsAfter > eventJobsBefore,
            boardRowsAfter != boardRowsBefore));
  }

  private static MarkerCreateRequest markerCreateRequest(MarkerGeoJsonPoint point) {
    return new MarkerCreateRequest(
        INCIDENT_UUID, OP_UUID, "CLUE", point, null, "SC-06 field clue", CLIENT_TS, 0L);
  }

  private static MarkerRecord onlyMarkerRow(InMemoryMarkerRepository repository) {
    List<MarkerRecord> records = repository.records();
    if (records.size() != 1) {
      throw new IllegalStateException("expected exactly one marker row, got " + records.size());
    }
    return records.get(0);
  }

  private static final class HarnessContext {

    private final InMemoryMarkerRepository markerRepository = new InMemoryMarkerRepository();
    private final InMemoryPhotoRepository photoRepository = new InMemoryPhotoRepository();
    private final MockObjectStorageAdapter storage = new MockObjectStorageAdapter();
    private final CapturingCurrentOpQuery currentOp = new CapturingCurrentOpQuery(OP_UUID);
    private final CapturingSseClient sse = new CapturingSseClient();
    private final CapturingBoardAssembler board = new CapturingBoardAssembler();
    private final CapturingEventDispatch eventDispatch = new CapturingEventDispatch(sse, board);
    private final CapturingMarkerEventPublisher markerEvents =
        new CapturingMarkerEventPublisher(eventDispatch);
    private final CapturingPhotoEventPublisher photoEvents =
        new CapturingPhotoEventPublisher(eventDispatch);
    private final HarnessMarkerWriteGuard markerGuard = new HarnessMarkerWriteGuard();
    private final HarnessPhotoWriteGuard photoGuard = new HarnessPhotoWriteGuard(currentOp);
    private final MarkerRequestContext markerContext =
        new MarkerRequestContext(authentication(), "idem-sc06-marker-create-001");
    private final PhotoRequestContext photoContext =
        new PhotoRequestContext(authentication(), "idem-sc06-photo-attach-001");
    private final MarkerCreateService markerCreateService =
        new MarkerCreateService(
            markerRepository,
            new MarkerLocationValidatorImpl(),
            new MarkerOpBindingValidator(currentOp),
            markerGuard,
            markerEvents,
            Clock.fixed(SERVER_TS, ZoneOffset.UTC),
            () -> MARKER_UUID);
    private final PhotoService photoService =
        new PhotoService(
            storage,
            photoRepository,
            photoGuard,
            photoEvents,
            Clock.fixed(SERVER_TS, ZoneOffset.UTC));

    static HarnessContext create() {
      return new HarnessContext();
    }

    CurrentOpEvidence currentOpEvidence(UUID requestedOpId) {
      return new CurrentOpEvidence(OP_ID, currentOp.findCalls(), OP_UUID.equals(requestedOpId));
    }

    private static SuriMapAuthentication authentication() {
      return new SuriMapAuthentication(ACCOUNT_UUID, "APP", POLICE_PHONE_UUID);
    }
  }

  private static final class CapturingCurrentOpQuery
      implements com.surimap.marker.domain.port.OperationalPeriodQueryPort {

    private final UUID currentOpId;
    private int findCalls;

    private CapturingCurrentOpQuery(UUID currentOpId) {
      this.currentOpId = currentOpId;
    }

    @Override
    public Optional<UUID> findCurrentOpId(UUID incidentId) {
      findCalls++;
      return Optional.ofNullable(currentOpId);
    }

    int findCalls() {
      return findCalls;
    }
  }

  private static final class HarnessMarkerWriteGuard implements MarkerWriteGuardPort {

    private boolean createAccessChecked;
    private SuriMapAuthentication authentication;

    @Override
    public void requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context) {
      createAccessChecked = true;
      authentication = context.authentication();
      if (!"APP".equals(authentication.channel())) {
        throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
      }
    }

    @Override
    public MarkerMutationContext requireUpdateAccess(UUID markerId, MarkerRequestContext context) {
      throw new UnsupportedOperationException("SC-06 harness does not update marker rows directly");
    }

    @Override
    public MarkerMutationContext requireDeleteAccess(UUID markerId, MarkerRequestContext context) {
      throw new UnsupportedOperationException("SC-06 harness does not delete marker rows");
    }

    AuthEvidence authEvidence() {
      return new AuthEvidence(
          authentication.channel(), ACCOUNT_ID, POLICE_PHONE_ID, true, true, createAccessChecked);
    }
  }

  private static final class HarnessPhotoWriteGuard implements PhotoWriteGuardPort {

    private final CapturingCurrentOpQuery currentOpQuery;
    private PhotoMarkerContext context;

    private HarnessPhotoWriteGuard(CapturingCurrentOpQuery currentOpQuery) {
      this.currentOpQuery = currentOpQuery;
    }

    void allow(PhotoMarkerContext context) {
      this.context = context;
    }

    @Override
    public PhotoMarkerContext requireUploadUrlAccess(UUID markerId, PhotoRequestContext request) {
      return contextFor(markerId, request);
    }

    @Override
    public PhotoMarkerContext requireAttachAccess(
        UUID markerId, UUID photoId, PhotoRequestContext request) {
      return contextFor(markerId, request);
    }

    private PhotoMarkerContext contextFor(UUID markerId, PhotoRequestContext request) {
      if (!"APP".equals(request.authentication().channel())) {
        throw new PhotoApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
      }
      if (context == null || !context.markerId().equals(markerId)) {
        throw new PhotoApiException("write_conflict", HttpStatus.CONFLICT);
      }
      UUID currentOpId =
          currentOpQuery
              .findCurrentOpId(context.incidentId())
              .orElseThrow(() -> new PhotoApiException("op_required", HttpStatus.CONFLICT));
      if (!currentOpId.equals(context.opId())) {
        throw new PhotoApiException("op_mismatch", HttpStatus.CONFLICT);
      }
      return context;
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
      eventDispatch.stage(MARKER_CREATE_EVENT_ID, request);
    }

    MarkerPublishRequest only() {
      if (published.size() != 1) {
        throw new IllegalStateException("expected one marker publish request");
      }
      return published.get(0);
    }

    List<MarkerPublishRequest> published() {
      return List.copyOf(published);
    }
  }

  private static final class CapturingPhotoEventPublisher implements PhotoEventPublisher {

    private final CapturingEventDispatch eventDispatch;
    private final List<PublishRequest> published = new ArrayList<>();

    private CapturingPhotoEventPublisher(CapturingEventDispatch eventDispatch) {
      this.eventDispatch = eventDispatch;
    }

    @Override
    public void publish(PublishRequest request) {
      published.add(request);
      eventDispatch.stage(PHOTO_ATTACH_EVENT_ID, request);
    }

    PublishRequest only() {
      if (published.size() != 1) {
        throw new IllegalStateException("expected one photo publish request");
      }
      return published.get(0);
    }

    List<PublishRequest> published() {
      return List.copyOf(published);
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
      EventDispatchJob job =
          new EventDispatchJob(
              eventId,
              request.type(),
              request.payload().id(),
              request.payload().status(),
              request.payload().version(),
              null,
              null,
              0L,
              0L,
              false);
      pendingJobs.add(job);
    }

    void stage(String eventId, PublishRequest request) {
      EventDispatchJob job =
          new EventDispatchJob(
              eventId,
              request.type(),
              request.payload().id(),
              request.payload().status(),
              request.payload().version(),
              request.payload().photoDelta().photoId(),
              request.payload().photoDelta().status(),
              request.payload().photoDelta().version(),
              0L,
              false);
      pendingJobs.add(job);
    }

    void commitAfterWrite() {
      for (EventDispatchJob pending : pendingJobs) {
        EventDispatchJob committed = pending.committed(1L + jobs.size());
        jobs.add(committed);
        sse.receive(committed);
        board.apply(committed);
      }
      pendingJobs.clear();
    }

    EventDispatchJob jobByEventId(String eventId) {
      return jobs.stream().filter(job -> job.eventId().equals(eventId)).findFirst().orElseThrow();
    }

    boolean containsEvent(String eventId) {
      return jobs.stream().anyMatch(job -> job.eventId().equals(eventId));
    }

    List<EventDispatchJob> jobs() {
      return List.copyOf(jobs);
    }
  }

  private static final class CapturingSseClient {

    private final List<SseMessage> messages = new ArrayList<>();

    void receive(EventDispatchJob job) {
      messages.add(
          new SseMessage(
              job.eventId(), job.entityId(), job.status(), job.version(), job.sequence()));
    }

    SseMessage messageByEventId(String eventId) {
      return messages.stream()
          .filter(message -> message.eventId().equals(eventId))
          .findFirst()
          .orElseThrow();
    }

    List<SseMessage> messages() {
      return List.copyOf(messages);
    }
  }

  private static final class CapturingBoardAssembler {

    private static final String BOARD_RESPONSE_ID = "bs-inc-precinct-first-001";

    private final BoardAssembler assembler = new BoardAssembler();
    private final List<BoardSourceRow> sourceRows = new ArrayList<>();
    private BoardDTO latestBoard = assemble(0L);
    private int refetchCalls;
    private int staleRefetchRejections;

    boolean apply(EventDispatchJob job) {
      if (!"MARKER_CREATED".equals(job.type()) && !"MARKER_UPDATED".equals(job.type())) {
        return false;
      }
      refetchCalls++;
      if (job.version() < latestBoard.boardResponseVersion()) {
        staleRefetchRejections++;
        return false;
      }
      sourceRows.removeIf(
          row -> row.slot().equals("marker") && row.sourceResponseId().equals(MARKER_ID));
      sourceRows.add(markerSourceRow(job));
      latestBoard = assemble(latestBoard.boardResponseVersion());
      return true;
    }

    boolean rejectStaleMarkerRefetch(MarkerPhoto attachedPhoto) {
      long beforeVersion = latestBoard.boardResponseVersion();
      int beforeRejections = staleRefetchRejections;
      boolean applied =
          apply(
              new EventDispatchJob(
                  "evt-s5-marker-updated-stale",
                  "MARKER_UPDATED",
                  MARKER_UUID,
                  "UPDATED",
                  1L,
                  attachedPhoto.id(),
                  attachedPhoto.status().name(),
                  1L,
                  refetchCalls + 100L,
                  true));
      return !applied
          && latestBoard.boardResponseVersion() == beforeVersion
          && staleRefetchRejections == beforeRejections + 1;
    }

    BoardMarkerRow rowByMarkerId(String markerId) {
      BoardSlotRow row = latestBoard.slotRow("marker", markerId);
      UUID internalPhotoId =
          row.payload().get("internalPhotoUuid") == null
              ? null
              : UUID.fromString(String.valueOf(row.payload().get("internalPhotoUuid")));
      return new BoardMarkerRow(
          row.id(),
          row.latestEventId(),
          row.version(),
          internalPhotoId,
          String.valueOf(row.payload().get("photoId")),
          String.valueOf(row.payload().get("photoStatus")),
          Long.parseLong(String.valueOf(row.payload().get("photoVersion"))),
          latestBoard.boardResponseId(),
          latestBoard.boardResponseVersion(),
          row.sourceSpec(),
          row.sourceHash(),
          latestBoard.slotSources().get("marker").size(),
          latestBoard.sourceVersions().get("marker").size(),
          latestBoard.sourceHashes().get("marker").size(),
          refetchCalls);
    }

    List<BoardMarkerRow> rows() {
      return latestBoard.slotSources().get("marker").stream()
          .map(cursor -> rowByMarkerId(cursor.id()))
          .toList();
    }

    private BoardDTO assemble(long boardResponseVersion) {
      return assembler.assemble(
          new BoardAssemblyRequest(
              INCIDENT_ID,
              BOARD_RESPONSE_ID,
              boardResponseVersion,
              OffsetDateTime.ofInstant(SERVER_TS, ZoneOffset.UTC),
              OP_ID,
              List.of(OP_ID),
              "geom-sc06-marker-area",
              sourceRows));
    }

    private static BoardSourceRow markerSourceRow(EventDispatchJob job) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("markerId", MARKER_ID);
      payload.put("internalMarkerUuid", String.valueOf(job.entityId()));
      payload.put("opId", OP_ID);
      payload.put("policePhoneId", POLICE_PHONE_ID);
      payload.put("photoId", PHOTO_ID);
      payload.put(
          "internalPhotoUuid", job.photoId() == null ? null : String.valueOf(job.photoId()));
      payload.put("photoStatus", job.photoDeltaStatus() == null ? "NONE" : job.photoDeltaStatus());
      payload.put("photoVersion", job.photoDeltaVersion());
      return new BoardSourceRow(
          "marker",
          "S5",
          MARKER_ID,
          "board-marker-" + MARKER_ALIAS,
          job.status(),
          job.version(),
          job.sequence(),
          job.eventId(),
          "S5:marker:" + MARKER_ALIAS + ":v" + job.version() + ":seq" + job.sequence(),
          payload);
    }
  }

  private record EventDispatchJob(
      String eventId,
      String type,
      UUID entityId,
      String status,
      long version,
      UUID photoId,
      String photoDeltaStatus,
      long photoDeltaVersion,
      long sequence,
      boolean committedAfterWrite) {

    EventDispatchJob committed(long sequence) {
      return new EventDispatchJob(
          eventId,
          type,
          entityId,
          status,
          version,
          photoId,
          photoDeltaStatus,
          photoDeltaVersion,
          sequence,
          true);
    }
  }

  private record SseMessage(
      String eventId, UUID entityId, String status, long version, long sequence) {}

  private record BoardMarkerRow(
      String markerId,
      String latestEventId,
      long markerVersion,
      UUID internalPhotoId,
      String photoId,
      String photoStatus,
      long photoVersion,
      String boardResponseId,
      long boardResponseVersion,
      String sourceSpec,
      String sourceHash,
      int slotSourceCount,
      int sourceVersionsCount,
      int sourceHashesCount,
      int refetchCalls) {}

  public static final record ScenarioEvidence(
      String scenarioId,
      String incidentId,
      String markerId,
      String photoId,
      AuthEvidence auth,
      PointGeometryEvidence geometry,
      CurrentOpEvidence currentOp,
      StorageEvidence storage,
      MarkerEventEvidence markerCreatedEvent,
      PhotoEventEvidence photoAttachedEvent,
      EventDispatchJobEvidence eventDispatchJob,
      SseEvidence sse,
      BoardEvidence board) {}

  public static final record AuthEvidence(
      String channel,
      String accountId,
      String policePhoneId,
      Boolean registered,
      Boolean assignedToIncident,
      Boolean guardChecked) {}

  public static final record PointGeometryEvidence(
      String type, List<String> coordinates, String srid, Boolean insideCurrentOverallSearchArea) {}

  public static final record CurrentOpEvidence(
      String opId, Integer queryCount, Boolean matchedRequestOp) {}

  public static final record StorageEvidence(
      String storageUri,
      String uploadUrl,
      String objectKey,
      String internalPhotoUuid,
      Boolean externalS3Called,
      Boolean attachedRowObserved) {}

  public static final record MarkerEventEvidence(
      String type,
      String status,
      String version,
      String opId,
      String policePhoneId,
      Integer capturedPublishCount,
      Boolean eventDispatchJobCaptured,
      String markerRowStatus,
      Integer markerRowCount) {}

  public static final record PhotoEventEvidence(
      String eventId,
      String type,
      String status,
      String version,
      String photoId,
      String internalPhotoUuid,
      String photoStatus,
      String photoVersion,
      String photoRowStatus,
      String photoRowVersion,
      Integer capturedPublishCount,
      Long photoRowCount) {}

  public static final record EventDispatchJobEvidence(
      String eventId,
      String entityId,
      String status,
      String version,
      String type,
      String photoDeltaStatus,
      Integer capturedJobCount,
      Boolean storedAfterCommit) {}

  public static final record SseEvidence(
      String eventId,
      String entityId,
      String version,
      String status,
      Integer capturedMessageCount) {}

  public static final record BoardEvidence(
      String slot,
      String latestEventId,
      String markerId,
      String photoId,
      String internalPhotoUuid,
      String photoStatus,
      String photoVersion,
      Integer markerRowCount,
      String boardResponseId,
      String boardResponseVersion,
      String sourceSpec,
      String sourceHash,
      Integer slotSourceCount,
      Integer sourceVersionsCount,
      Integer sourceHashesCount,
      Integer refetchCalls,
      Boolean staleRefetchRejected,
      Boolean converged) {}

  public static final record InvalidGeometryEvidence(
      InvalidPointGeometryEvidence geometry, RejectionEvidence rejection) {}

  public static final record InvalidPointGeometryEvidence(String fixture, String coordinates) {}

  public static final record RejectionEvidence(
      String httpStatus,
      String error,
      Integer markerRowsBefore,
      Integer markerRowsAfter,
      Long photoRowsBefore,
      Long photoRowsAfter,
      Integer eventDispatchJobsBefore,
      Integer eventDispatchJobsAfter,
      Integer boardRowsBefore,
      Integer boardRowsAfter,
      Boolean markerRowCreated,
      Boolean photoRowCreated,
      Boolean eventDispatchJobCreated,
      Boolean boardResponseChanged) {}
}
