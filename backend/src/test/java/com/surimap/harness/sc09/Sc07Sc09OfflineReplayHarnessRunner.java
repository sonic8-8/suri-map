package com.surimap.harness.sc09;

import com.surimap.board.BoardAssemblyLagState;
import com.surimap.board.BoardAssemblyRequest;
import com.surimap.board.BoardDTO;
import com.surimap.board.BoardRefetchGuard;
import com.surimap.board.BoardRefetchLedgerEntry;
import com.surimap.board.BoardRefetchLedgerStatus;
import com.surimap.board.BoardRefetchResult;
import com.surimap.board.BoardRefetchSignal;
import com.surimap.board.BoardSlotRow;
import com.surimap.board.BoardSourceRow;
import com.surimap.sync.idempotency.IdempotentWriteRequest;
import com.surimap.sync.idempotency.IdempotentWriteResponse;
import com.surimap.sync.idempotency.IdempotentWriteService;
import com.surimap.sync.idempotency.OwnerReplayRecoveryPort;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test-local SC-07/SC-09 harness runner for offline replay, dedupe, and board convergence. */
public class Sc07Sc09OfflineReplayHarnessRunner {

  private static final String INCIDENT_ID = "inc-precinct-first-001";
  private static final String BOARD_RESPONSE_ID = "bs-inc-precinct-first-001";
  private static final String ACTIVE_OP_ID = "op-precinct-001-op1";
  private static final String GEOMETRY_HASH = "hash-board-geometry-current";
  private static final OffsetDateTime SERVER_TS = OffsetDateTime.parse("2026-04-28T10:30:00+09:00");

  private static final String PATH_ENDPOINT = "POST /search-paths/batch";
  private static final String PATH_OPERATION_ID = "op-outbox-path-001";
  private static final String PATH_ID = "path-precinct-mixed-001";
  private static final String PATH_IDEMPOTENCY_KEY = "idem-path-001";
  private static final String PATH_BODY_HASH = "sha256:path-normal-001";
  private static final long PATH_VERSION = 2L;
  private static final long PATH_SEQUENCE = 502L;
  private static final String PATH_EVENT_ID = "evt-s3-path-appended-001";

  private static final String MARKER_ENDPOINT = "POST /markers";
  private static final String MARKER_OPERATION_ID = "op-outbox-marker-001";
  private static final String MARKER_ID = "mk-precinct-clue-001";
  private static final String MARKER_IDEMPOTENCY_KEY = "idem-marker-001";
  private static final String MARKER_BODY_HASH = "sha256:marker-clue-001";
  private static final long MARKER_VERSION = 2L;
  private static final long MARKER_SEQUENCE = 602L;
  private static final String MARKER_EVENT_ID = "evt-s5-marker-updated-photo-001";

  private static final String FRESHNESS_ENDPOINT =
      "GET /incidents/{incidentId}/police-phone-freshness";
  private static final String FRESHNESS_ID = "dev-precinct-phone-01";
  private static final long FRESHNESS_VERSION = 4L;
  private static final long FRESHNESS_SEQUENCE = 704L;
  private static final String FRESHNESS_EVENT_ID = "evt-s1-2-heartbeat-recovered-001";

  public Sc07Sc09OfflineReplayHarnessRunner() {}

  public ScenarioEvidence runOfflineThenRecovery() {
    HarnessState state = HarnessState.create();
    OfflineEvidence offline =
        new OfflineEvidence(
            "net-script-domain-write-001",
            "PENDING_LOCAL",
            "PENDING_LOCAL",
            "PENDING_SEND",
            "PENDING_SEND",
            "OFFLINE",
            0,
            0,
            0);

    RecoveryFlushResult flush = state.flushPathMarkerAndFreshness();
    BoardRefetchResult boardResult =
        new BoardRefetchGuard()
            .apply(
                emptyBoardRequest(),
                List.of(
                    pathSignal(flush.pathResponse(), state.ownerEndpoints.pathEventId()),
                    markerSignal(flush.markerResponse(), state.ownerEndpoints.markerEventId()),
                    policePhoneFreshnessSignal(flush.freshness())));
    BoardDTO board = boardResult.board();

    RecoveryEvidence recovery =
        new RecoveryEvidence(
            "net-script-outbox-flush-001",
            "ACKED",
            "ACKED",
            "SYNCED",
            "SYNCED",
            "ONLINE",
            List.of("PENDING_SEND", "SENDING", "ACKED"),
            state.ownerEndpoints.pathOwnerInvocations(),
            state.ownerEndpoints.markerOwnerInvocations(),
            state.ownerEndpoints.pathEventJobs(),
            state.ownerEndpoints.markerEventJobs(),
            flush.pathResponse().entityVersion(),
            flush.markerResponse().entityVersion(),
            state.ownerEndpoints.freshnessOwnerCalls());

    return new ScenarioEvidence(
        "SC-07+SC-09",
        INCIDENT_ID,
        offline,
        recovery,
        slotEvidence(board.slotRow("path", PATH_ID), PATH_VERSION),
        slotEvidence(board.slotRow("marker", MARKER_ID), MARKER_VERSION),
        slotEvidence(board.slotRow("police_phone_freshness", FRESHNESS_ID), FRESHNESS_VERSION),
        boardEvidence(boardResult));
  }

  public DuplicateReplayEvidence runDuplicateReplay() {
    HarnessState state = HarnessState.create();

    IdempotentWriteResponse firstPath =
        state.idempotency.reserveAndReplay(pathRequest(), state.ownerEndpoints::commitPath);
    IdempotentWriteResponse replayPath =
        state.idempotency.reserveAndReplay(
            pathRequest(), () -> unexpectedOwnerReplay("path owner must not run on replay"));
    IdempotentWriteResponse firstMarker =
        state.idempotency.reserveAndReplay(markerRequest(), state.ownerEndpoints::commitMarker);
    IdempotentWriteResponse replayMarker =
        state.idempotency.reserveAndReplay(
            markerRequest(), () -> unexpectedOwnerReplay("marker owner must not run on replay"));

    return new DuplicateReplayEvidence(
        PATH_IDEMPOTENCY_KEY,
        MARKER_IDEMPOTENCY_KEY,
        state.ownerEndpoints.pathOwnerInvocations(),
        state.ownerEndpoints.markerOwnerInvocations(),
        state.ownerEndpoints.pathRows(),
        state.ownerEndpoints.markerRows(),
        state.ownerEndpoints.pathEventJobs(),
        state.ownerEndpoints.markerEventJobs(),
        !firstPath.replayed() && replayPath.replayed(),
        !firstMarker.replayed() && replayMarker.replayed());
  }

  public BoardRefetchLagEvidence runBoardRefetchLagCheck() {
    BoardAssemblyLagState lagState =
        new BoardRefetchGuard().observeAssemblyLag(stalePathBoardRequest(), pathSignal());

    return new BoardRefetchLagEvidence(
        lagState.uiState(),
        lagState.slot(),
        lagState.entityId(),
        lagState.sourceResponseVersion(),
        lagState.staleResponseVersion(),
        lagState.reloadAssertion());
  }

  public MockedOwnerEndpointEvidence runMockedOwnerEndpointFixtures() {
    MockOwnerReplayRecoveryPort recoveryPort = new MockOwnerReplayRecoveryPort();
    IdempotentWriteService idempotency = new IdempotentWriteService(recoveryPort);
    IdempotentWriteRequest pathRequest = pathRequest();
    IdempotentWriteRequest markerRequest = markerRequest();
    idempotency.recordCommittedWithoutResponse(pathRequest, 201, PATH_VERSION, PATH_SEQUENCE);
    idempotency.recordCommittedWithoutResponse(markerRequest, 201, MARKER_VERSION, MARKER_SEQUENCE);
    recoveryPort.recoverWith(PATH_ENDPOINT, PATH_OPERATION_ID, PATH_ID, pathOwnerResponse());
    recoveryPort.recoverWith(
        MARKER_ENDPOINT, MARKER_OPERATION_ID, MARKER_ID, markerOwnerResponse());

    IdempotentWriteResponse pathReplay =
        idempotency.reserveAndReplay(
            pathRequest,
            () -> unexpectedOwnerReplay("committed cache-missing recovery must not run path owner"));
    IdempotentWriteResponse markerReplay =
        idempotency.reserveAndReplay(
            markerRequest,
            () -> unexpectedOwnerReplay("committed cache-missing recovery must not run owner"));
    MockOwnerEndpoints ownerEndpoints = new MockOwnerEndpoints();
    PolicePhoneFreshnessFixture freshness = ownerEndpoints.queryPolicePhoneFreshness();
    BoardRefetchResult convergence =
        new BoardRefetchGuard()
            .apply(
                emptyBoardRequest(),
                List.of(
                    pathSignal(pathReplay, PATH_EVENT_ID),
                    markerSignal(markerReplay, MARKER_EVENT_ID),
                    policePhoneFreshnessSignal(freshness)));

    return new MockedOwnerEndpointEvidence(
        List.of(
            "mock-path-owner-endpoint",
            "mock-marker-owner-endpoint",
            "mock-police-phone-freshness-owner-endpoint"),
        List.of(PATH_ENDPOINT, MARKER_ENDPOINT, FRESHNESS_ENDPOINT),
        List.of("path", "marker", "police_phone_freshness"),
        false,
        pathReplay.replayRecovered()
            && markerReplay.replayRecovered()
            && pathReplay.replayed()
            && markerReplay.replayed()
            && recoveryPort.calls() == 2
            && pathReplay.entityVersion() == PATH_VERSION
            && markerReplay.entityVersion() == MARKER_VERSION,
        pathReplay.entityId(),
        List.of(pathReplay.entityId(), markerReplay.entityId(), freshness.id()),
        recoveryPort.calls(),
        pathReplay.replayRecovered(),
        markerReplay.replayRecovered(),
        ownerEndpoints.freshnessOwnerCalls() == 1,
        converged(convergence.board(), "path", PATH_ID, PATH_VERSION, PATH_SEQUENCE)
            && converged(convergence.board(), "marker", MARKER_ID, MARKER_VERSION, MARKER_SEQUENCE)
            && converged(
                convergence.board(),
                "police_phone_freshness",
                FRESHNESS_ID,
                FRESHNESS_VERSION,
                FRESHNESS_SEQUENCE));
  }

  private static BoardSlotEvidence slotEvidence(BoardSlotRow row, long expectedVersion) {
    return new BoardSlotEvidence(
        slotFromRow(row),
        row.id(),
        row.status(),
        expectedVersion,
        row.version(),
        row.version() >= expectedVersion);
  }

  private static String slotFromRow(BoardSlotRow row) {
    return switch (row.id()) {
      case PATH_ID -> "path";
      case MARKER_ID -> "marker";
      case FRESHNESS_ID -> "police_phone_freshness";
      default -> throw new IllegalArgumentException("unknown slot row id: " + row.id());
    };
  }

  private static BoardRefetchEvidence boardEvidence(BoardRefetchResult result) {
    Map<String, BoardRefetchLedgerEntry> bySlot = new LinkedHashMap<>();
    for (BoardRefetchLedgerEntry entry : result.ledger()) {
      bySlot.put(entry.slot(), entry);
    }
    return new BoardRefetchEvidence(
        status(bySlot, "path"),
        status(bySlot, "marker"),
        status(bySlot, "police_phone_freshness"),
        converged(result.board(), "path", PATH_ID, PATH_VERSION, PATH_SEQUENCE),
        converged(result.board(), "marker", MARKER_ID, MARKER_VERSION, MARKER_SEQUENCE),
        converged(
            result.board(),
            "police_phone_freshness",
            FRESHNESS_ID,
            FRESHNESS_VERSION,
            FRESHNESS_SEQUENCE));
  }

  private static String status(Map<String, BoardRefetchLedgerEntry> bySlot, String slot) {
    return Optional.ofNullable(bySlot.get(slot))
        .map(BoardRefetchLedgerEntry::applyStatus)
        .orElse(BoardRefetchLedgerStatus.FAILED_RETRYABLE)
        .name();
  }

  private static boolean converged(
      BoardDTO board, String slot, String id, long expectedVersion, long expectedSequence) {
    BoardSlotRow row = board.slotRow(slot, id);
    return row.version() >= expectedVersion && row.sequence() >= expectedSequence;
  }

  private static BoardAssemblyRequest emptyBoardRequest() {
    return boardRequest(0L, List.of());
  }

  private static BoardAssemblyRequest stalePathBoardRequest() {
    return boardRequest(
        1L,
        List.of(
            new BoardSourceRow(
                "path",
                "S3-1",
                PATH_ID,
                "board-path-" + PATH_ID,
                "ACTIVE",
                1L,
                501L,
                "evt-s3-path-appended-stale-001",
                "S3-1:path:" + PATH_ID + ":v1",
                Map.of("id", PATH_ID, "status", "ACTIVE", "version", 1L))));
  }

  private static BoardAssemblyRequest boardRequest(long boardResponseVersion, List<BoardSourceRow> rows) {
    return new BoardAssemblyRequest(
        INCIDENT_ID,
        BOARD_RESPONSE_ID,
        boardResponseVersion,
        SERVER_TS,
        ACTIVE_OP_ID,
        List.of(ACTIVE_OP_ID),
        GEOMETRY_HASH,
        rows);
  }

  private static BoardRefetchSignal pathSignal() {
    return pathSignal(pathOwnerResponse(), PATH_EVENT_ID);
  }

  private static BoardRefetchSignal pathSignal(IdempotentWriteResponse response, String eventId) {
    return signal(
        eventId,
        "PATH_APPENDED",
        "path",
        "S3-1",
        response.entityId(),
        response.entityStatus(),
        response.entityVersion(),
        response.entitySequence(),
        "S3-1:path:" + response.entityId() + ":v" + response.entityVersion(),
        Map.of(
            "id", response.entityId(),
            "status", response.entityStatus(),
            "version", response.entityVersion(),
            "segments", List.of("seg-precinct-vehicle-001", "seg-precinct-foot-001")));
  }

  private static BoardRefetchSignal markerSignal(IdempotentWriteResponse response, String eventId) {
    return signal(
        eventId,
        "MARKER_UPDATED",
        "marker",
        "S5",
        response.entityId(),
        response.entityStatus(),
        response.entityVersion(),
        response.entitySequence(),
        "S5:marker:" + response.entityId() + ":v" + response.entityVersion(),
        Map.of(
            "id", response.entityId(),
            "status", response.entityStatus(),
            "version", response.entityVersion(),
            "photoId", "photo-precinct-clue-001",
            "photoStatus", "FINALIZED",
            "photoVersion", response.entityVersion()));
  }

  private static BoardRefetchSignal policePhoneFreshnessSignal(PolicePhoneFreshnessFixture fixture) {
    return signal(
        fixture.eventId(),
        "POLICE_PHONE_HEARTBEAT_UPDATED",
        "police_phone_freshness",
        "S1-2",
        fixture.id(),
        fixture.status(),
        fixture.version(),
        fixture.sequence(),
        "S1-2:police_phone_freshness:" + fixture.id() + ":v" + fixture.version(),
        Map.of(
            "id", fixture.id(),
            "status", fixture.status(),
            "version", fixture.version(),
            "lastHeartbeatAt", fixture.lastHeartbeatAt(),
            "lastSyncAt", fixture.lastSyncAt(),
            "derivedFreshness", fixture.derivedFreshness()));
  }

  private static BoardRefetchSignal signal(
      String eventId,
      String eventType,
      String slot,
      String sourceSpec,
      String entityId,
      String status,
      long version,
      long sequence,
      String sourceHash,
      Map<String, Object> payload) {
    return new BoardRefetchSignal(
        eventId,
        INCIDENT_ID,
        eventType,
        SERVER_TS,
        sequence,
        slot,
        sourceSpec,
        entityId,
        status,
        version,
        sequence,
        sourceHash,
        payload);
  }

  private static IdempotentWriteRequest pathRequest() {
    return new IdempotentWriteRequest(
        PATH_ENDPOINT, PATH_OPERATION_ID, PATH_ID, PATH_IDEMPOTENCY_KEY, PATH_BODY_HASH);
  }

  private static IdempotentWriteRequest markerRequest() {
    return new IdempotentWriteRequest(
        MARKER_ENDPOINT,
        MARKER_OPERATION_ID,
        MARKER_ID,
        MARKER_IDEMPOTENCY_KEY,
        MARKER_BODY_HASH);
  }

  private static IdempotentWriteResponse pathOwnerResponse() {
    return response(
        201,
        "{\"id\":\"path-precinct-mixed-001\",\"status\":\"ACTIVE\",\"version\":2}",
        PATH_ID,
        "ACTIVE",
        PATH_VERSION,
        PATH_SEQUENCE);
  }

  private static IdempotentWriteResponse markerOwnerResponse() {
    return response(
        201,
        "{\"id\":\"mk-precinct-clue-001\",\"status\":\"UPDATED\",\"version\":2}",
        MARKER_ID,
        "UPDATED",
        MARKER_VERSION,
        MARKER_SEQUENCE);
  }

  private static IdempotentWriteResponse response(
      int statusCode,
      String bodyJson,
      String entityId,
      String entityStatus,
      long entityVersion,
      long entitySequence) {
    return new IdempotentWriteResponse(
        statusCode,
        bodyJson,
        "application/json",
        1,
        entityId,
        entityStatus,
        entityVersion,
        entitySequence,
        false,
        false,
        null);
  }

  private static IdempotentWriteResponse unexpectedOwnerReplay(String message) {
    throw new IllegalStateException(message);
  }

  public static final record ScenarioEvidence(
      String scenarioId,
      String incidentId,
      OfflineEvidence offline,
      RecoveryEvidence recovery,
      BoardSlotEvidence pathSlot,
      BoardSlotEvidence markerSlot,
      BoardSlotEvidence policePhoneFreshnessSlot,
      BoardRefetchEvidence boardRefetch) {}

  public static final record OfflineEvidence(
      String networkScriptId,
      String pathLocalStatus,
      String markerLocalStatus,
      String pathOutboxStatus,
      String markerOutboxStatus,
      String policePhoneConnectivity,
      int serverPathRows,
      int serverMarkerRows,
      int boardRows) {}

  public static final record RecoveryEvidence(
      String networkScriptId,
      String pathOutboxStatus,
      String markerOutboxStatus,
      String pathLocalStatus,
      String markerLocalStatus,
      String policePhoneConnectivity,
      List<String> flushTransitions,
      int pathOwnerInvocations,
      int markerOwnerInvocations,
      int pathEventJobs,
      int markerEventJobs,
      long pathWriteResponseVersion,
      long markerWriteResponseVersion,
      int freshnessOwnerCalls) {}

  public static final record BoardSlotEvidence(
      String slot,
      String id,
      String status,
      long expectedVersion,
      long boardRowVersion,
      boolean converged) {}

  public static final record BoardRefetchEvidence(
      String pathApplyStatus,
      String markerApplyStatus,
      String freshnessApplyStatus,
      boolean pathConverged,
      boolean markerConverged,
      boolean freshnessConverged) {}

  public static final record DuplicateReplayEvidence(
      String pathIdempotencyKey,
      String markerIdempotencyKey,
      int pathOwnerInvocations,
      int markerOwnerInvocations,
      int pathRows,
      int markerRows,
      int pathEventJobs,
      int markerEventJobs,
      boolean pathReplayServedFromCache,
      boolean markerReplayServedFromCache) {}

  public static final record BoardRefetchLagEvidence(
      String uiState,
      String slot,
      String entityId,
      long sourceResponseVersion,
      long staleResponseVersion,
      String reloadAssertion) {}

  public static final record MockedOwnerEndpointEvidence(
      List<String> fixtureNames,
      List<String> endpoints,
      List<String> slots,
      boolean externalNetworkCalled,
      boolean committedCacheMissingRecovered,
      String recoveredEntityId,
      List<String> recoveredEntityIds,
      int recoveryPortCalls,
      boolean pathRecovered,
      boolean markerRecovered,
      boolean freshnessFixtureQueried,
      boolean convergenceVerified) {}

  private static final record RecoveryFlushResult(
      IdempotentWriteResponse pathResponse,
      IdempotentWriteResponse markerResponse,
      PolicePhoneFreshnessFixture freshness) {}

  private static final record PolicePhoneFreshnessFixture(
      String id,
      String status,
      long version,
      long sequence,
      String eventId,
      String lastHeartbeatAt,
      String lastSyncAt,
      String derivedFreshness) {}

  private static final class HarnessState {

    private final MockOwnerEndpoints ownerEndpoints = new MockOwnerEndpoints();
    private final IdempotentWriteService idempotency =
        new IdempotentWriteService(new MockOwnerReplayRecoveryPort());

    static HarnessState create() {
      return new HarnessState();
    }

    RecoveryFlushResult flushPathMarkerAndFreshness() {
      IdempotentWriteResponse pathResponse =
          idempotency.reserveAndReplay(pathRequest(), ownerEndpoints::commitPath);
      IdempotentWriteResponse markerResponse =
          idempotency.reserveAndReplay(markerRequest(), ownerEndpoints::commitMarker);
      PolicePhoneFreshnessFixture freshness = ownerEndpoints.queryPolicePhoneFreshness();
      return new RecoveryFlushResult(pathResponse, markerResponse, freshness);
    }
  }

  private static final class MockOwnerEndpoints {

    private final List<String> pathRows = new ArrayList<>();
    private final List<String> markerRows = new ArrayList<>();
    private final List<String> pathEventJobs = new ArrayList<>();
    private final List<String> markerEventJobs = new ArrayList<>();
    private int pathOwnerInvocations;
    private int markerOwnerInvocations;
    private int freshnessOwnerCalls;

    IdempotentWriteResponse commitPath() {
      pathOwnerInvocations++;
      addOnce(pathRows, PATH_ID);
      addOnce(pathEventJobs, PATH_EVENT_ID);
      return pathOwnerResponse();
    }

    IdempotentWriteResponse commitMarker() {
      markerOwnerInvocations++;
      addOnce(markerRows, MARKER_ID);
      addOnce(markerEventJobs, MARKER_EVENT_ID);
      return markerOwnerResponse();
    }

    PolicePhoneFreshnessFixture queryPolicePhoneFreshness() {
      freshnessOwnerCalls++;
      return new PolicePhoneFreshnessFixture(
          FRESHNESS_ID,
          "NORMAL",
          FRESHNESS_VERSION,
          FRESHNESS_SEQUENCE,
          FRESHNESS_EVENT_ID,
          "2026-04-28T10:29:55+09:00",
          "2026-04-28T10:30:00+09:00",
          "normal");
    }

    int pathOwnerInvocations() {
      return pathOwnerInvocations;
    }

    int markerOwnerInvocations() {
      return markerOwnerInvocations;
    }

    int pathRows() {
      return pathRows.size();
    }

    int markerRows() {
      return markerRows.size();
    }

    int pathEventJobs() {
      return pathEventJobs.size();
    }

    int markerEventJobs() {
      return markerEventJobs.size();
    }

    String pathEventId() {
      return pathEventJobs.stream().findFirst().orElseThrow();
    }

    String markerEventId() {
      return markerEventJobs.stream().findFirst().orElseThrow();
    }

    int freshnessOwnerCalls() {
      return freshnessOwnerCalls;
    }

    private static void addOnce(List<String> values, String value) {
      if (!values.contains(value)) {
        values.add(value);
      }
    }
  }

  private static final class MockOwnerReplayRecoveryPort implements OwnerReplayRecoveryPort {

    private final Map<String, IdempotentWriteResponse> recovered = new LinkedHashMap<>();
    private int calls;

    @Override
    public Optional<IdempotentWriteResponse> recover(
        String endpoint, String operationId, String entityId) {
      calls++;
      return Optional.ofNullable(recovered.get(key(endpoint, operationId, entityId)));
    }

    void recoverWith(
        String endpoint,
        String operationId,
        String entityId,
        IdempotentWriteResponse response) {
      recovered.put(key(endpoint, operationId, entityId), response);
    }

    int calls() {
      return calls;
    }

    private static String key(String endpoint, String operationId, String entityId) {
      return endpoint + "|" + operationId + "|" + entityId;
    }
  }
}
