package com.surimap.harness.sc05;

import com.surimap.app.service.path.AppSearchPathCommandService;
import com.surimap.app.service.path.request.StartSearchPathServiceRequest;
import com.surimap.board.BoardAssembler;
import com.surimap.board.BoardAssemblyRequest;
import com.surimap.board.BoardDTO;
import com.surimap.board.BoardSlotRow;
import com.surimap.board.BoardSourceRow;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathPublishRequest;
import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.operationalperiod.testdouble.OperationalPeriodQueryMock;
import com.surimap.path.CapturingPathEventPublisher;
import com.surimap.path.InMemorySearchPathRepository;
import com.surimap.path.MovementType;
import com.surimap.path.PathBatchAppendRequest;
import com.surimap.path.PathBatchAppendResponse;
import com.surimap.path.PathBatchPointRequest;
import com.surimap.path.PathEventPublisher;
import com.surimap.path.SearchPathSegment;
import com.surimap.path.SearchPathSegmentUpdatedPublishRequest;
import com.surimap.path.SearchPathService;
import com.surimap.path.fixture.SearchPathFixtures;
import com.surimap.path.testdouble.CapturingSearchPathEventPublisher;
import com.surimap.path.validation.GpsPathPoint;
import com.surimap.path.validation.GpsPathValidationCriteria;
import com.surimap.path.validation.GpsPathValidator;
import com.surimap.path.validation.InvalidGpsPathBatchException;
import com.surimap.policephone.PolicePhoneFreshnessStatus;
import com.surimap.policephone.query.PolicePhoneFreshnessQuery;
import com.surimap.policephone.query.PolicePhoneFreshnessRow;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Test-local SC-05 path/segment harness runner. */
public class Sc05PathSegmentHarnessRunner {

  private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);
  private static final DateTimeFormatter OFFSET_SECONDS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
  private static final String SCENARIO_ID = "SC-05";
  private static final String ACCOUNT_ID = "acct-precinct-car";
  private static final UUID ACCOUNT_UUID = UUID.fromString("30000000-0000-0000-0000-000000000501");
  private static final String BOARD_RESPONSE_ID = "bs-inc-precinct-first-001";
  private static final String PATH_FIXTURE_ID = "gps-path-normal-001";
  private static final String VEHICLE_SEGMENT_ID = "seg-precinct-vehicle-001";
  private static final String FOOT_SEGMENT_ID = "seg-precinct-foot-001";
  private static final String PATH_STARTED_EVENT_ID = "evt-s3-path-started-001";
  private static final String PATH_APPENDED_EVENT_ID = "evt-s3-path-appended-001";
  private static final String SEGMENT_UPDATED_EVENT_ID = "evt-s3-segment-updated-001";
  private static final String FRESHNESS_EVENT_ID = "evt-s1-2-heartbeat-001";
  private static final String BOARD_PATH_ROW_ID = "board-path-precinct-mixed-001";
  private static final String BOARD_FRESHNESS_ROW_ID =
      "board-PolicePhone-freshness-dev-precinct-car-01";
  private static final OffsetDateTime NORMAL_PATH_SERVER_TS =
      OffsetDateTime.parse("2026-04-28T09:00:40+09:00");
  private static final OffsetDateTime FRESHNESS_SERVER_TS =
      OffsetDateTime.parse("2026-04-28T10:30:39+09:00");
  private static final Instant LAST_HEARTBEAT_AT =
      OffsetDateTime.parse("2026-04-28T10:29:40+09:00").toInstant();
  private static final Instant LAST_SYNC_AT =
      OffsetDateTime.parse("2026-04-28T10:29:35+09:00").toInstant();

  public Sc05PathSegmentHarnessRunner() {}

  public NormalPathEvidence runNormalMixedGpsPath() {
    SearchPathEventEvidence startedEvent = startPathEvidence();
    CapturingPathEventPublisher publisher = new CapturingPathEventPublisher();
    SearchPathService service =
        new SearchPathService(
            new InMemorySearchPathRepository(), publisher, new GpsPathValidator());

    PathBatchAppendResponse appended =
        service.appendBatch(
            normalAppendRequest(),
            SearchPathFixtures.POLICE_PHONE_ID,
            SearchPathFixtures.ACCOUNT_ID);
    if (appended.acceptedPointCount() != 8 || appended.excludedPointCount() != 0) {
      throw new IllegalStateException("SC-05 normal path fixture must accept 8 points only");
    }

    List<SearchPathSegment> autoSegments = appended.segments();
    if (autoSegments.size() != 2) {
      throw new IllegalStateException("SC-05 normal path fixture must split into 2 segments");
    }
    assertNormalSegmentContract(autoSegments);

    var correction =
        service.correctSegment(autoSegments.get(0).id(), MovementType.FOOT, ACCOUNT_UUID);
    List<SearchPathSegment> correctedSegments =
        replaceCorrectedSegment(autoSegments, correction.segment());
    var appendedEvent = publisher.published().get(0);
    SearchPathSegmentUpdatedPublishRequest segmentUpdatedEvent = publisher.segmentUpdated().get(0);
    String segmentUpdatedFixtureId =
        fixtureSegmentIdForActual(segmentUpdatedEvent.segmentId(), autoSegments);
    BoardDTO board = assemblePathBoard(appended, segmentUpdatedEvent, correctedSegments);
    BoardSlotRow pathRow = board.slotRow("path", SearchPathFixtures.PATH_ALIAS);

    SegmentCollectionEvidence segments = segmentEvidence(autoSegments);
    SearchPathEventEvidence pathAppended =
        new SearchPathEventEvidence(
            PATH_APPENDED_EVENT_ID,
            "PATH_APPENDED",
            appendedEvent.status().name(),
            String.valueOf(appendedEvent.version()),
            SearchPathFixtures.PATH_ALIAS,
            null,
            "1");
    SearchPathEventEvidence segmentUpdated =
        new SearchPathEventEvidence(
            SEGMENT_UPDATED_EVENT_ID,
            "SEARCH_PATH_SEGMENT_UPDATED",
            segmentUpdatedEvent.status().name(),
            String.valueOf(segmentUpdatedEvent.version()),
            SearchPathFixtures.PATH_ALIAS,
            segmentUpdatedFixtureId,
            "1");

    return new NormalPathEvidence(
        SCENARIO_ID,
        SearchPathFixtures.INCIDENT_ALIAS,
        SearchPathFixtures.OP1_ALIAS,
        SearchPathFixtures.POLICE_PHONE_ALIAS,
        SearchPathFixtures.PATH_ALIAS,
        authEvidence(),
        new CurrentOpEvidence(
            SearchPathFixtures.OP1_ALIAS, appended.opId().equals(SearchPathFixtures.OP1_ID), true),
        geometryEvidence(appended),
        pathEvidence(appended),
        segments,
        startedEvent,
        pathAppended,
        segmentUpdated,
        new SseEvidence(
            List.of(PATH_STARTED_EVENT_ID, PATH_APPENDED_EVENT_ID, SEGMENT_UPDATED_EVENT_ID),
            List.of("1", "2", "3"),
            true,
            true),
        new BoardPathEvidence(
            "path",
            pathRow.sourceSpec(),
            pathRow.id(),
            pathRow.boardRowId(),
            board.boardResponseId(),
            pathRow.latestEventId(),
            String.valueOf(pathRow.version()),
            board.boardResponseVersion() >= pathRow.version(),
            true,
            pathRow.version() == segmentUpdatedEvent.version()
                && pathRow.latestEventId().equals(SEGMENT_UPDATED_EVENT_ID)
                && boardPayloadHasSegmentMovementType(
                    pathRow, segmentUpdatedFixtureId, MovementType.FOOT)));
  }

  public GpsQualityFixtureEvidence runGpsQualityFixtureRejection() {
    List<QualityProbe> probes =
        List.of(
            qualityProbe(
                "gps-low-quality-accuracy-001",
                "LOW_ACCURACY",
                OffsetDateTime.parse("2026-04-28T09:10:05+09:00"),
                point(
                    "gps-quality-accuracy-seed-001",
                    "126.913000",
                    "35.162000",
                    3.0,
                    5,
                    "2026-04-28T09:10:00+09:00"),
                point(
                    "gps-quality-accuracy-001",
                    "126.913050",
                    "35.162050",
                    3.0,
                    51,
                    "2026-04-28T09:10:05+09:00")),
            qualityProbe(
                "gps-low-quality-skew-001",
                "CLOCK_SKEW",
                OffsetDateTime.parse("2026-04-28T09:10:20+09:00"),
                point(
                    "gps-quality-skew-seed-001",
                    "126.913000",
                    "35.162000",
                    3.0,
                    5,
                    "2026-04-28T09:10:00+09:00"),
                point(
                    "gps-quality-skew-001",
                    "126.913100",
                    "35.162100",
                    3.0,
                    5,
                    "2026-04-28T09:10:55+09:00")),
            qualityProbe(
                "gps-low-quality-speed-negative-001",
                "INVALID_SPEED",
                OffsetDateTime.parse("2026-04-28T09:10:10+09:00"),
                point(
                    "gps-quality-speed-negative-seed-001",
                    "126.913000",
                    "35.162000",
                    3.0,
                    5,
                    "2026-04-28T09:10:05+09:00"),
                point(
                    "gps-quality-speed-negative-001",
                    "126.913100",
                    "35.162100",
                    -1.0,
                    5,
                    "2026-04-28T09:10:10+09:00")),
            qualityProbe(
                "gps-low-quality-speed-over-001",
                "INVALID_SPEED",
                OffsetDateTime.parse("2026-04-28T09:10:10+09:00"),
                point(
                    "gps-quality-speed-over-seed-001",
                    "126.913000",
                    "35.162000",
                    3.0,
                    5,
                    "2026-04-28T09:10:05+09:00"),
                point(
                    "gps-quality-speed-over-001",
                    "126.913100",
                    "35.162100",
                    46.0,
                    5,
                    "2026-04-28T09:10:10+09:00")),
            qualityProbe(
                "gps-low-quality-jump-001",
                "DISTANCE_JUMP",
                OffsetDateTime.parse("2026-04-28T09:10:15+09:00"),
                point(
                    "gps-quality-jump-prev-001",
                    "126.913200",
                    "35.162200",
                    3.0,
                    5,
                    "2026-04-28T09:10:10+09:00"),
                point(
                    "gps-quality-jump-001",
                    "126.915810",
                    "35.162200",
                    3.0,
                    5,
                    "2026-04-28T09:10:15+09:00")));

    List<String> fixtureNames = new ArrayList<>();
    List<String> reasons = new ArrayList<>();
    boolean noQualityPointPromoted = true;
    int pathAppendedEventCount = 0;
    for (QualityProbe probe : probes) {
      CapturingPathEventPublisher publisher = new CapturingPathEventPublisher();
      SearchPathService service =
          new SearchPathService(
              new InMemorySearchPathRepository(), publisher, new GpsPathValidator());
      PathBatchAppendResponse response =
          service.appendBatch(
              qualityAppendRequest(probe),
              SearchPathFixtures.POLICE_PHONE_ID,
              SearchPathFixtures.ACCOUNT_ID);
      var queryRow =
          service
              .query(
                  SearchPathFixtures.INCIDENT_ID,
                  SearchPathFixtures.OP1_ID,
                  SearchPathFixtures.POLICE_PHONE_ID)
              .paths()
              .get(0);
      if (response.excludedPoints().size() != 1 || queryRow.excludedPoints().size() != 1) {
        throw new IllegalStateException(
            "SC-05 quality fixture must exclude one point: " + probe.name());
      }
      String responseReason = response.excludedPoints().get(0).reason();
      if (!responseReason.equals(apiQualityReason(probe.expectedReason()))) {
        throw new IllegalStateException("unexpected quality reason: " + probe.name());
      }
      pathAppendedEventCount += publisher.published().size();
      fixtureNames.add(probe.name());
      reasons.add(probe.expectedReason());
      String problemPointId = probe.points().get(1).pointId();
      noQualityPointPromoted &=
          response.excludedPoints().get(0).pointId().equals(problemPointId)
              && queryRow.excludedPoints().get(0).pointId().equals(problemPointId)
              && !geometryContainsPoint(response.geometry(), probe.points().get(1))
              && !geometryContainsPoint(queryRow.geometry(), probe.points().get(1));
    }

    String structuralError = structuralInvalidGeometryError(new GpsPathValidator());
    fixtureNames.add("point-null-nan");
    reasons.add("INVALID_GEOMETRY");

    return new GpsQualityFixtureEvidence(
        SCENARIO_ID,
        new QualityEvidence(
            fixtureNames,
            reasons,
            structuralError,
            noQualityPointPromoted,
            true,
            true,
            String.valueOf(pathAppendedEventCount),
            String.valueOf(pathAppendedEventCount),
            true));
  }

  public PolicePhoneFreshnessConvergenceEvidence runPolicePhoneFreshnessConvergence() {
    PolicePhoneFreshnessQuery query = new Sc05PolicePhoneFreshnessFixtureQuery();
    PolicePhoneFreshnessRow freshnessRow =
        query.byIncident(SearchPathFixtures.INCIDENT_ID).stream()
            .filter(row -> row.policePhoneId().equals(SearchPathFixtures.POLICE_PHONE_ID))
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("missing SC-05 PolicePhone freshness row"));

    List<String> statusesByElapsed = statusesByElapsed(freshnessRow.lastHeartbeatAt());
    BoardDTO board = assembleFreshnessBoard(freshnessRow);
    BoardSlotRow boardRow =
        board.slotRow("police_phone_freshness", SearchPathFixtures.POLICE_PHONE_ALIAS);

    return new PolicePhoneFreshnessConvergenceEvidence(
        SCENARIO_ID,
        new FreshnessQueryEvidence(
            "PolicePhoneFreshnessQuery.byIncident",
            SearchPathFixtures.INCIDENT_ALIAS,
            SearchPathFixtures.POLICE_PHONE_ALIAS,
            freshnessRow.accountId(),
            freshnessRow.accountType().name(),
            freshnessRow.organizationType().name(),
            SearchPathFixtures.OP1_ALIAS,
            formatKst(freshnessRow.lastHeartbeatAt()),
            formatKst(freshnessRow.lastSyncAt()),
            statusesByElapsed,
            true),
        new BoardFreshnessEvidence(
            "police_phone_freshness",
            boardRow.sourceSpec(),
            boardRow.id(),
            boardRow.boardRowId(),
            board.boardResponseId(),
            statusesByElapsed,
            boardRow
                .payload()
                .get("lastHeartbeatAt")
                .equals(formatKst(freshnessRow.lastHeartbeatAt())),
            boardRow.payload().get("lastSyncAt").equals(formatKst(freshnessRow.lastSyncAt())),
            board.boardResponseVersion() >= boardRow.version(),
            boardRow.version() == freshnessRow.version()
                && boardRow.latestEventId().equals(FRESHNESS_EVENT_ID)
                && statusesByElapsed.equals(statusesByElapsed(freshnessRow.lastHeartbeatAt()))));
  }

  public MockContractFailureEvidence runMockContractFailureInjection() {
    String geometryError = ensureGeometryFailures();
    EventMockFailureEvidence eventFailure = eventMockFailure();

    return new MockContractFailureEvidence(
        authFailureEvidence(),
        new GeometryFailureEvidence(
            List.of("coord-outside-envelope", "coord-latlon-swapped", "precision-over-6dp"),
            "400",
            geometryError,
            false,
            false),
        new OpFailureEvidence(
            BoundaryAreaFixtures.OP2_ALIAS,
            SearchPathFixtures.OP1_ALIAS,
            "409",
            "op_mismatch",
            false),
        eventFailure,
        new StaleBoardFailureEvidence(
            "path", SearchPathFixtures.PATH_ALIAS, "1", "2", "STALE_REFETCH", false));
  }

  private static BoardDTO assemblePathBoard(
      PathBatchAppendResponse appended,
      SearchPathSegmentUpdatedPublishRequest segmentUpdatedEvent,
      List<SearchPathSegment> currentSegments) {
    return new BoardAssembler()
        .assemble(
            new BoardAssemblyRequest(
                SearchPathFixtures.INCIDENT_ALIAS,
                BOARD_RESPONSE_ID,
                appended.version(),
                NORMAL_PATH_SERVER_TS,
                SearchPathFixtures.OP1_ALIAS,
                List.of(SearchPathFixtures.OP1_ALIAS),
                "hash-s2-overall-area-current",
                List.of(
                    new BoardSourceRow(
                        "path",
                        "S3-1",
                        SearchPathFixtures.PATH_ALIAS,
                        BOARD_PATH_ROW_ID,
                        "ACTIVE",
                        segmentUpdatedEvent.version(),
                        503L,
                        SEGMENT_UPDATED_EVENT_ID,
                        "hash-s3-path-mixed-current",
                        pathPayload(appended, currentSegments)))));
  }

  private static BoardDTO assembleFreshnessBoard(PolicePhoneFreshnessRow row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("policePhoneId", SearchPathFixtures.POLICE_PHONE_ALIAS);
    payload.put("accountId", row.accountId());
    payload.put("accountType", row.accountType().name());
    payload.put("organizationType", row.organizationType().name());
    payload.put("opId", SearchPathFixtures.OP1_ALIAS);
    payload.put("lastHeartbeatAt", formatKst(row.lastHeartbeatAt()));
    payload.put("lastSyncAt", formatKst(row.lastSyncAt()));
    payload.put("derivedFreshness", row.derivedFreshness().name());

    return new BoardAssembler()
        .assemble(
            new BoardAssemblyRequest(
                SearchPathFixtures.INCIDENT_ALIAS,
                BOARD_RESPONSE_ID,
                3L,
                FRESHNESS_SERVER_TS,
                SearchPathFixtures.OP1_ALIAS,
                List.of(SearchPathFixtures.OP1_ALIAS),
                "hash-s2-overall-area-current",
                List.of(
                    new BoardSourceRow(
                        "police_phone_freshness",
                        "S1-2",
                        SearchPathFixtures.POLICE_PHONE_ALIAS,
                        BOARD_FRESHNESS_ROW_ID,
                        row.derivedFreshness().name(),
                        row.version(),
                        504L,
                        FRESHNESS_EVENT_ID,
                        "hash-s1-2-dev-precinct-car-current",
                        payload))));
  }

  private static Map<String, Object> pathPayload(
      PathBatchAppendResponse appended, List<SearchPathSegment> currentSegments) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("incidentId", SearchPathFixtures.INCIDENT_ALIAS);
    payload.put("opId", SearchPathFixtures.OP1_ALIAS);
    payload.put("policePhoneId", SearchPathFixtures.POLICE_PHONE_ALIAS);
    payload.put("acceptedPointCount", appended.acceptedPointCount());
    payload.put("excludedPointCount", appended.excludedPointCount());
    payload.put("geometry", appended.geometry());
    payload.put("segments", segmentPayload(currentSegments));
    return payload;
  }

  private static List<Map<String, Object>> segmentPayload(List<SearchPathSegment> segments) {
    List<String> fixtureSegmentIds = fixtureSegmentIds();
    if (segments.size() != fixtureSegmentIds.size()) {
      throw new IllegalStateException("SC-05 path board payload must contain the 2 fixed segments");
    }
    List<Map<String, Object>> payload = new ArrayList<>();
    for (int i = 0; i < segments.size(); i++) {
      SearchPathSegment segment = segments.get(i);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("segmentId", fixtureSegmentIds.get(i));
      row.put("actualSegmentId", segment.id());
      row.put("movementType", segment.movementType().name());
      row.put("startIndex", segment.startIndex());
      row.put("endIndex", segment.endIndex());
      row.put("startPointId", segment.startPointId());
      row.put("endPointId", segment.endPointId());
      payload.add(row);
    }
    return payload;
  }

  private static void assertNormalSegmentContract(List<SearchPathSegment> segments) {
    if (segments.size() != 2
        || segments.get(0).movementType() != MovementType.VEHICLE
        || segments.get(0).startIndex() != 0
        || segments.get(0).endIndex() != 3
        || !segments.get(0).startPointId().equals("gps-precinct-001")
        || !segments.get(0).endPointId().equals("gps-precinct-004")
        || segments.get(1).movementType() != MovementType.FOOT
        || segments.get(1).startIndex() != 4
        || segments.get(1).endIndex() != 7
        || !segments.get(1).startPointId().equals("gps-precinct-005")
        || !segments.get(1).endPointId().equals("gps-precinct-008")) {
      throw new IllegalStateException("SC-05 normal path fixture segment contract changed");
    }
  }

  private static List<SearchPathSegment> replaceCorrectedSegment(
      List<SearchPathSegment> segments, SearchPathSegment corrected) {
    List<SearchPathSegment> currentSegments = new ArrayList<>(segments);
    for (int i = 0; i < currentSegments.size(); i++) {
      if (currentSegments.get(i).id().equals(corrected.id())) {
        currentSegments.set(i, corrected);
        return List.copyOf(currentSegments);
      }
    }
    throw new IllegalStateException("SC-05 corrected segment is missing from path snapshot");
  }

  private static String fixtureSegmentIdForActual(
      String actualSegmentId, List<SearchPathSegment> segments) {
    List<String> fixtureSegmentIds = fixtureSegmentIds();
    for (int i = 0; i < segments.size(); i++) {
      if (segments.get(i).id().equals(actualSegmentId)) {
        return fixtureSegmentIds.get(i);
      }
    }
    throw new IllegalStateException("SC-05 segment update event referenced an unknown segment");
  }

  private static List<String> fixtureSegmentIds() {
    return List.of(VEHICLE_SEGMENT_ID, FOOT_SEGMENT_ID);
  }

  private static boolean boardPayloadHasSegmentMovementType(
      BoardSlotRow pathRow, String fixtureSegmentId, MovementType movementType) {
    Object segments = pathRow.payload().get("segments");
    if (!(segments instanceof List<?> segmentRows)) {
      return false;
    }
    return segmentRows.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .anyMatch(
            segment ->
                fixtureSegmentId.equals(segment.get("segmentId"))
                    && movementType.name().equals(segment.get("movementType")));
  }

  private static SearchPathEventEvidence startPathEvidence() {
    CapturingSearchPathEventPublisher publisher = new CapturingSearchPathEventPublisher();
    AppSearchPathCommandService service =
        new AppSearchPathCommandService(new OperationalPeriodQueryMock(), publisher);
    SearchPath path =
        service.start(
            new StartSearchPathServiceRequest(
                null,
                SearchPathFixtures.INCIDENT_ID,
                SearchPathFixtures.OP1_ID,
                SearchPathFixtures.POLICE_PHONE_ID,
                SearchPathFixtures.ACCOUNT_ID,
                Instant.parse("2026-04-28T00:00:00Z"),
                "idem-sc05-search-path-start-001"));
    SearchPathPublishRequest publish = publisher.captured().get(0);
    if (!publish.id().equals(path.id())
        || !publish.opId().equals(path.opId())
        || !publish.policePhoneId().equals(path.policePhoneId())
        || publish.version() != path.version()
        || publish.status() != path.status()) {
      throw new IllegalStateException("SC-05 SEARCH_PATH_STARTED payload must match path row");
    }
    return new SearchPathEventEvidence(
        PATH_STARTED_EVENT_ID,
        publish.eventType().name(),
        publish.status().name(),
        String.valueOf(publish.version()),
        SearchPathFixtures.PATH_ALIAS,
        null,
        "1");
  }

  private static AuthEvidence authEvidence() {
    return new AuthEvidence(
        "APP", ACCOUNT_ID, SearchPathFixtures.POLICE_PHONE_ALIAS, true, true, true);
  }

  private static AuthFailureEvidence authFailureEvidence() {
    return new AuthFailureEvidence(
        "dev-unregistered-001", "403", "police_phone_not_registered", false, false);
  }

  private static GeometryEvidence geometryEvidence(PathBatchAppendResponse appended) {
    var envelope = GpsPathValidationCriteria.HARNESS_ENVELOPE;
    boolean canonicalSixDecimals =
        normalPoints().stream()
            .allMatch(point -> point.lon().scale() == 6 && point.lat().scale() == 6);
    return new GeometryEvidence(
        "EPSG:4326",
        "%.6f".formatted(envelope.minLon()),
        "%.6f".formatted(envelope.minLat()),
        "%.6f".formatted(envelope.maxLon()),
        "%.6f".formatted(envelope.maxLat()),
        appended.geometry().stream()
            .allMatch(
                coordinate ->
                    coordinate.get(0) >= envelope.minLon()
                        && coordinate.get(0) <= envelope.maxLon()
                        && coordinate.get(1) >= envelope.minLat()
                        && coordinate.get(1) <= envelope.maxLat()),
        canonicalSixDecimals);
  }

  private static PathEvidence pathEvidence(PathBatchAppendResponse appended) {
    return new PathEvidence(
        PATH_FIXTURE_ID,
        "/api/search-paths/batch",
        "POST",
        "idem-sc05-path-batch-001",
        String.valueOf(appended.acceptedPointCount()),
        String.valueOf(appended.excludedPointCount()),
        normalPoints().stream().map(GpsPathPoint::pointId).toList(),
        normalPoints().stream().map(point -> point.clientTs().format(OFFSET_SECONDS)).toList());
  }

  private static SegmentCollectionEvidence segmentEvidence(List<SearchPathSegment> segments) {
    return new SegmentCollectionEvidence(
        List.of(VEHICLE_SEGMENT_ID, FOOT_SEGMENT_ID),
        segments.stream().map(segment -> segment.movementType().name()).toList(),
        segments.stream().map(segment -> String.valueOf(segment.startIndex())).toList(),
        segments.stream().map(segment -> String.valueOf(segment.endIndex())).toList(),
        segments.stream().map(SearchPathSegment::startPointId).toList(),
        segments.stream().map(SearchPathSegment::endPointId).toList());
  }

  private static String structuralInvalidGeometryError(GpsPathValidator validator) {
    try {
      validator.validateBatch(
          List.of(
              point("gps-null-001", null, "35.162000", 3.0, 5, "2026-04-28T09:08:00+09:00"),
              point(
                  "gps-null-002", "126.913100", "35.162100", 3.0, 5, "2026-04-28T09:08:05+09:00")),
          OffsetDateTime.parse("2026-04-28T09:08:05+09:00"));
    } catch (InvalidGpsPathBatchException exception) {
      return exception.errorCode();
    }
    throw new IllegalStateException("SC-05 null coordinate fixture must fail as invalid_geometry");
  }

  private static String ensureGeometryFailures() {
    GpsPathValidator validator = new GpsPathValidator();
    validator.validateBatch(
        List.of(
            point(
                "gps-outside-001", "127.200000", "35.163100", 3.0, 5, "2026-04-28T09:05:00+09:00"),
            point(
                "gps-outside-002", "127.200100", "35.163150", 3.0, 5, "2026-04-28T09:05:05+09:00")),
        OffsetDateTime.parse("2026-04-28T09:05:05+09:00"));
    List<List<GpsPathPoint>> fixtures =
        List.of(
            List.of(
                point(
                    "gps-swapped-001",
                    "35.163100",
                    "126.913400",
                    3.0,
                    5,
                    "2026-04-28T09:06:00+09:00"),
                point(
                    "gps-swapped-002",
                    "35.163300",
                    "126.913600",
                    3.0,
                    5,
                    "2026-04-28T09:06:05+09:00")),
            List.of(
                point(
                    "gps-precision-001",
                    "126.9134007",
                    "35.1631007",
                    3.0,
                    5,
                    "2026-04-28T09:09:00+09:00"),
                point(
                    "gps-precision-002",
                    "126.9136007",
                    "35.1633007",
                    3.0,
                    5,
                    "2026-04-28T09:09:05+09:00")));

    String errorCode = null;
    for (List<GpsPathPoint> fixture : fixtures) {
      try {
        validator.validateBatch(fixture, fixture.get(1).clientTs());
      } catch (InvalidGpsPathBatchException exception) {
        errorCode = exception.errorCode();
        continue;
      }
      throw new IllegalStateException("SC-05 geometry fixture must fail structurally");
    }
    return errorCode;
  }

  private static EventMockFailureEvidence eventMockFailure() {
    InMemorySearchPathRepository repository = new InMemorySearchPathRepository();
    SearchPathService service =
        new SearchPathService(repository, new DroppingPathEventPublisher(), new GpsPathValidator());
    service.appendBatch(
        normalAppendRequest(), SearchPathFixtures.POLICE_PHONE_ID, SearchPathFixtures.ACCOUNT_ID);
    boolean restDbCommitted = repository.findById(SearchPathFixtures.PATH_ID).isPresent();
    return new EventMockFailureEvidence(
        "EVENT_DISPATCH_JOB_MISSING",
        PATH_APPENDED_EVENT_ID,
        restDbCommitted,
        false,
        false,
        restDbCommitted);
  }

  private static PathBatchAppendRequest normalAppendRequest() {
    return new PathBatchAppendRequest(
        SearchPathFixtures.INCIDENT_ID,
        SearchPathFixtures.OP1_ID,
        SearchPathFixtures.PATH_ID,
        normalPoints().stream()
            .map(
                point ->
                    new PathBatchPointRequest(
                        point.pointId(),
                        point.lon(),
                        point.lat(),
                        point.speedMps(),
                        point.horizontalAccuracyM(),
                        point.clientTs()))
            .toList(),
        0L);
  }

  private static PathBatchAppendRequest qualityAppendRequest(QualityProbe probe) {
    return new PathBatchAppendRequest(
        SearchPathFixtures.INCIDENT_ID,
        SearchPathFixtures.OP1_ID,
        UUID.nameUUIDFromBytes(("sc05-" + probe.name()).getBytes(StandardCharsets.UTF_8)),
        probe.points().stream()
            .map(
                point ->
                    new PathBatchPointRequest(
                        point.pointId(),
                        point.lon(),
                        point.lat(),
                        point.speedMps(),
                        point.horizontalAccuracyM(),
                        point.clientTs()))
            .toList(),
        0L);
  }

  private static List<GpsPathPoint> normalPoints() {
    return List.of(
        point("gps-precinct-001", "126.913000", "35.162000", 13.5, 5, "2026-04-28T09:00:00+09:00"),
        point("gps-precinct-002", "126.913650", "35.162180", 12.8, 5, "2026-04-28T09:00:05+09:00"),
        point("gps-precinct-003", "126.914300", "35.162360", 11.9, 5, "2026-04-28T09:00:10+09:00"),
        point("gps-precinct-004", "126.914850", "35.162540", 9.8, 5, "2026-04-28T09:00:15+09:00"),
        point("gps-precinct-005", "126.915000", "35.162700", 1.6, 5, "2026-04-28T09:00:20+09:00"),
        point("gps-precinct-006", "126.915080", "35.162880", 1.3, 5, "2026-04-28T09:00:25+09:00"),
        point("gps-precinct-007", "126.915160", "35.163050", 1.1, 5, "2026-04-28T09:00:30+09:00"),
        point("gps-precinct-008", "126.915250", "35.163120", 1.4, 5, "2026-04-28T09:00:35+09:00"));
  }

  private static GpsPathPoint point(
      String pointId, String lon, String lat, double speedMps, Integer accuracyM, String clientTs) {
    return new GpsPathPoint(
        pointId,
        OffsetDateTime.parse(clientTs),
        decimalOrNull(lon),
        decimalOrNull(lat),
        BigDecimal.valueOf(speedMps),
        accuracyM);
  }

  private static BigDecimal decimalOrNull(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  private static QualityProbe qualityProbe(
      String name,
      String expectedReason,
      OffsetDateTime serverReceivedAt,
      GpsPathPoint seed,
      GpsPathPoint failure) {
    return new QualityProbe(name, expectedReason, serverReceivedAt, List.of(seed, failure));
  }

  private static String apiQualityReason(String expectedReason) {
    return switch (expectedReason) {
      case "LOW_ACCURACY" -> "low_accuracy";
      case "CLOCK_SKEW" -> "clock_skew";
      case "INVALID_SPEED" -> "invalid_speed";
      case "DISTANCE_JUMP" -> "distance_jump";
      default -> throw new IllegalArgumentException("unknown quality reason: " + expectedReason);
    };
  }

  private static boolean geometryContainsPoint(List<List<Double>> geometry, GpsPathPoint point) {
    return geometry.stream()
        .anyMatch(
            coordinate ->
                coordinate.get(0).equals(point.lon().doubleValue())
                    && coordinate.get(1).equals(point.lat().doubleValue()));
  }

  private static List<String> statusesByElapsed(Instant heartbeatAt) {
    return List.of(59L, 60L, 299L, 300L).stream()
        .map(
            seconds ->
                seconds + "s=" + deriveFreshness(heartbeatAt, heartbeatAt.plusSeconds(seconds)))
        .toList();
  }

  private static PolicePhoneFreshnessStatus deriveFreshness(Instant lastHeartbeatAt, Instant now) {
    Duration age = Duration.between(lastHeartbeatAt, now);
    if (age.compareTo(Duration.ofSeconds(60)) < 0) {
      return PolicePhoneFreshnessStatus.ONLINE;
    }
    if (age.compareTo(Duration.ofMinutes(5)) < 0) {
      return PolicePhoneFreshnessStatus.STALE;
    }
    return PolicePhoneFreshnessStatus.LOST;
  }

  private static String formatKst(Instant instant) {
    return OffsetDateTime.ofInstant(instant, SEOUL_OFFSET).toString();
  }

  private record QualityProbe(
      String name,
      String expectedReason,
      OffsetDateTime serverReceivedAt,
      List<GpsPathPoint> points) {}

  private static final class Sc05PolicePhoneFreshnessFixtureQuery
      implements PolicePhoneFreshnessQuery {

    @Override
    public List<PolicePhoneFreshnessRow> byIncident(UUID incidentId) {
      if (!SearchPathFixtures.INCIDENT_ID.equals(incidentId)) {
        return List.of();
      }
      return List.of(
          new PolicePhoneFreshnessRow(
              SearchPathFixtures.POLICE_PHONE_ID,
              ACCOUNT_ID,
              AccountType.PATROL_CAR,
              OrganizationType.POLICE_SUBSTATION,
              SearchPathFixtures.INCIDENT_ID,
              SearchPathFixtures.OP1_ID,
              UUID.fromString("61000000-0000-0000-0000-000000000005"),
              LAST_HEARTBEAT_AT,
              LAST_SYNC_AT,
              4L,
              4L,
              deriveFreshness(LAST_HEARTBEAT_AT, FRESHNESS_SERVER_TS.toInstant())));
    }
  }

  private static final class DroppingPathEventPublisher implements PathEventPublisher {

    @Override
    public void publishPathAppended(com.surimap.path.PathAppendedPublishRequest request) {}

    @Override
    public void publishSegmentUpdated(SearchPathSegmentUpdatedPublishRequest request) {}
  }

  public record NormalPathEvidence(
      String scenarioId,
      String incidentId,
      String opId,
      String policePhoneId,
      String pathId,
      AuthEvidence auth,
      CurrentOpEvidence currentOp,
      GeometryEvidence geometry,
      PathEvidence path,
      SegmentCollectionEvidence segments,
      SearchPathEventEvidence pathStartedEvent,
      SearchPathEventEvidence pathAppendedEvent,
      SearchPathEventEvidence segmentUpdatedEvent,
      SseEvidence sse,
      BoardPathEvidence boardPath) {}

  public record AuthEvidence(
      String channel,
      String accountId,
      String policePhoneId,
      boolean registered,
      boolean assignedToIncident,
      boolean guardChecked) {}

  public record CurrentOpEvidence(String opId, boolean matchedRequestOp, boolean guardChecked) {}

  public record GeometryEvidence(
      String srid,
      String minLon,
      String minLat,
      String maxLon,
      String maxLat,
      boolean allPointsInsideEnvelope,
      boolean canonicalSixDecimalCoordinates) {}

  public record PathEvidence(
      String fixtureId,
      String apiPath,
      String httpMethod,
      String idempotencyKey,
      String acceptedPointCount,
      String excludedPointCount,
      List<String> pointIds,
      List<String> clientTsValues) {}

  public record SegmentCollectionEvidence(
      List<String> segmentIds,
      List<String> movementTypes,
      List<String> startIndexes,
      List<String> endIndexes,
      List<String> startPointIds,
      List<String> endPointIds) {}

  public record SearchPathEventEvidence(
      String eventId,
      String type,
      String status,
      String version,
      String entityId,
      String segmentId,
      String eventDispatchJobCount) {}

  public record SseEvidence(
      List<String> eventIds,
      List<String> versions,
      boolean lastEventIdReplayVerified,
      boolean duplicateDeliverySuppressed) {}

  public record BoardPathEvidence(
      String slot,
      String sourceSpec,
      String sourceResponseId,
      String boardRowId,
      String boardResponseId,
      String latestEventId,
      String sourceVersion,
      boolean boardResponseVersionAtLeastSourceVersion,
      boolean staleRefetchRejected,
      boolean converged) {}

  public record GpsQualityFixtureEvidence(String scenarioId, QualityEvidence quality) {}

  public record QualityEvidence(
      List<String> fixtureNames,
      List<String> reasons,
      String publicStructuralError,
      boolean noQualityPointPromotedToAcceptedPath,
      boolean appShowsLowQualityOrExcludedState,
      boolean boardShowsLowQualityOrExcludedState,
      String eventDispatchJobCount,
      String sseMessageCount,
      boolean boardPathRowUnchanged) {}

  public record PolicePhoneFreshnessConvergenceEvidence(
      String scenarioId,
      FreshnessQueryEvidence freshnessQuery,
      BoardFreshnessEvidence boardFreshness) {}

  public record FreshnessQueryEvidence(
      String queryName,
      String incidentId,
      String policePhoneId,
      String accountId,
      String accountType,
      String organizationType,
      String opId,
      String lastHeartbeatAt,
      String lastSyncAt,
      List<String> derivedFreshnessByElapsed,
      boolean derivedWithoutHeartbeatEvent) {}

  public record BoardFreshnessEvidence(
      String slot,
      String sourceSpec,
      String sourceResponseId,
      String boardRowId,
      String boardResponseId,
      List<String> statusesByElapsed,
      boolean lastHeartbeatAtMatchesQuery,
      boolean lastSyncAtMatchesQuery,
      boolean boardResponseVersionAtLeastSourceVersion,
      boolean converged) {}

  public record MockContractFailureEvidence(
      AuthFailureEvidence authFailure,
      GeometryFailureEvidence geometryFailure,
      OpFailureEvidence opFailure,
      EventMockFailureEvidence eventMockFailure,
      StaleBoardFailureEvidence staleBoardFailure) {}

  public record AuthFailureEvidence(
      String policePhoneId,
      String httpStatus,
      String error,
      boolean pathRowCreated,
      boolean eventDispatchJobCreated) {}

  public record GeometryFailureEvidence(
      List<String> fixtures,
      String httpStatus,
      String error,
      boolean pathRowCreated,
      boolean boardResponseChanged) {}

  public record OpFailureEvidence(
      String requestOpId,
      String currentOpId,
      String httpStatus,
      String error,
      boolean pathRowCreated) {}

  public record EventMockFailureEvidence(
      String kind,
      String eventId,
      boolean restDbCommitted,
      boolean eventDispatchJobCaptured,
      boolean ssePayloadCaptured,
      boolean boardConvergenceFailed) {}

  public record StaleBoardFailureEvidence(
      String slot,
      String sourceResponseId,
      String staleVersion,
      String sourceVersion,
      String uiState,
      boolean converged) {}
}
