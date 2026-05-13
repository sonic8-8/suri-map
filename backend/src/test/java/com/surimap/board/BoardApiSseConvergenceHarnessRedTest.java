package com.surimap.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L6-T10B RED: board API/SSE convergence harness from S3-2/S4 fixture contracts. */
@DisplayName("L6-T10B board API and SSE convergence harness")
class BoardApiSseConvergenceHarnessRedTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Sc02ToSc12BoardConvergenceHarnessRunner HARNESS =
      new Sc02ToSc12BoardConvergenceHarnessRunner();
  private static final String ACTIVE_OP_ID = "op-precinct-001-op2";
  private static final List<String> SELECTED_OP_IDS =
      List.of("op-precinct-001-op1", "op-precinct-001-op2");
  private static final String GEOMETRY_HASH = "hash-board-geometry-current";

  @Test
  @DisplayName("board API assembly harness exposes every S3-2 board_api_base slot row")
  void board_api_assembly_harness_exposes_every_s3_2_board_api_base_slot_row() throws IOException {
    Sc02ToSc12BoardConvergenceHarnessRunner.BoardApiAssemblyCoverageReport report =
        HARNESS.boardApiAssemblyCoverage();
    BoardDTO board = report.board();
    List<BoardSourceRow> expectedRows = report.sourceRows();

    assertThat(board.boardResponseVersion())
        .as("board_api_base boardResponseVersion must dominate every source row version")
        .isGreaterThanOrEqualTo(
            expectedRows.stream().mapToLong(BoardSourceRow::version).max().orElseThrow());
    assertThat(board.slotSources().keySet())
        .as("BoardDTO.slotSources must use the exact spec/boundaries.md section 9.2 slots")
        .containsExactlyElementsOf(BoardSlotRegistry.requiredSlots());

    for (BoardSourceRow expected : expectedRows) {
      assertCursorPresent("slotSources source coverage", report.slotSources(), expected);
      assertCursorPresent("sourceVersions source coverage", report.sourceVersions(), expected);
      assertCursorPresent("sourceHashes source coverage", report.sourceHashes(), expected);
      if (report.visibleInBoard(expected)) {
        assertCursorPresent("visible BoardDTO.slotSources", board.slotSources(), expected);
        assertCursorPresent("visible BoardDTO.sourceVersions", board.sourceVersions(), expected);
        assertCursorPresent("visible BoardDTO.sourceHashes", board.sourceHashes(), expected);
        assertThat(board.slotRow(expected.slot(), expected.sourceResponseId()))
            .as("visible slot row %s/%s", expected.slot(), expected.sourceResponseId())
            .satisfies(row -> assertCursor(row, expected));
      } else {
        assertThat(report.hiddenReason(expected))
            .as("hidden visible row reason for %s/%s", expected.slot(), expected.sourceResponseId())
            .contains("closed_incident_hides_police_phone_freshness");
        assertThat(board.slotSources().get(expected.slot()))
            .as("closed incident visible BoardDTO keeps police_phone_freshness private")
            .isEmpty();
        assertThat(board.slots().get(expected.slot()))
            .as("closed incident response slots do not leak police_phone_freshness")
            .isEqualTo(List.of());
        assertThat(board.sourceVersions().get(expected.slot()))
            .as("closed incident sourceVersions do not leak police_phone_freshness")
            .isEmpty();
        assertThat(board.sourceHashes().get(expected.slot()))
            .as("closed incident sourceHashes do not leak police_phone_freshness")
            .isEmpty();
        assertThatThrownBy(() -> board.slotRow(expected.slot(), expected.sourceResponseId()))
            .isInstanceOf(NoSuchElementException.class);
      }
    }
  }

  @Test
  @DisplayName("slot merge follows S3-2 api_assembly_failure_fixtures")
  void slot_merge_follows_s3_2_api_assembly_failure_fixtures() throws IOException {
    JsonNode failures =
        fixture("docs/spec/specs/S3-2.json").at("/harness_fixtures/api_assembly_failure_fixtures");
    BoardRefetchGuard guard = new BoardRefetchGuard();
    BoardAssemblyRequest current = currentMergeRequest();

    BoardRefetchResult duplicate =
        guard.apply(
            current, List.of(signal(toastRow(text(failures.at("/duplicate_event"), "eventId")))));
    assertThat(duplicate.ledger())
        .singleElement()
        .satisfies(
            entry -> {
              JsonNode expected = failures.at("/duplicate_event");
              assertThat(entry.eventId()).isEqualTo(text(expected, "eventId"));
              assertThat(entry.slot()).isEqualTo(text(expected, "slot"));
              assertThat(entry.entityId()).isEqualTo(text(expected, "entityId"));
              assertThat(entry.applyStatus().name())
                  .isEqualTo(text(expected, "expectedLedgerStatus"));
            });
    assertThat(slotRows(duplicate.board(), "toast"))
        .hasSize((int) number(failures.at("/duplicate_event"), "expectedBoardRowCount"));
    assertThat(duplicate.convergenceProbe()).isNull();

    JsonNode staleFixture = failures.at("/stale_lower_version");
    BoardRefetchResult stale = guard.apply(current, List.of(signal(stalePathRow(staleFixture))));
    assertThat(stale.ledger())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.eventId()).isEqualTo(text(staleFixture, "eventId"));
              assertThat(entry.applyStatus().name())
                  .isEqualTo(text(staleFixture, "expectedLedgerStatus"));
              assertThat(entry.previousVersion()).isEqualTo(number(staleFixture, "currentVersion"));
              assertThat(entry.version()).isEqualTo(number(staleFixture, "version"));
              assertThat(entry.previousSequence())
                  .isEqualTo(number(staleFixture, "currentSequence"));
              assertThat(entry.sequence()).isEqualTo(number(staleFixture, "sequence"));
            });
    assertCurrentPathRowUnchanged(stale.board());
    assertThat(stale.convergenceProbe()).isNull();

    JsonNode missingFixture = failures.at("/missing_predecessor");
    BoardRefetchResult missing =
        guard.apply(current, List.of(signal(missingPredecessorPathRow(missingFixture))));
    assertThat(missing.ledger())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.eventId()).isEqualTo(text(missingFixture, "eventId"));
              assertThat(entry.applyStatus().name())
                  .isEqualTo(text(missingFixture, "expectedLedgerStatus"));
              assertThat(entry.reloadReason().name())
                  .isEqualTo(text(missingFixture, "expectedReloadReason"));
              assertThat(entry.previousSequence())
                  .isEqualTo(number(missingFixture, "cursorLastSequence"));
              assertThat(entry.sequence()).isEqualTo(number(missingFixture, "sequence"));
            });
    assertCurrentPathRowUnchanged(missing.board());
    assertThat(missing.convergenceProbe()).isNull();

    JsonNode goneFixture = failures.at("/gone_refetch_required");
    BoardRefetchResult gone = guard.goneRefetchRequired(current, text(goneFixture, "lastEventId"));
    assertThat(gone.ledger())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.eventId()).isEqualTo(text(goneFixture, "lastEventId"));
              assertThat(entry.applyStatus().name())
                  .isEqualTo(text(goneFixture, "expectedCursorState"));
              assertThat(entry.reloadReason().name())
                  .isEqualTo(text(goneFixture, "expectedReloadReason"));
            });
    assertCurrentPathRowUnchanged(gone.board());
    assertThat(gone.convergenceProbe()).isNull();
  }

  @Test
  @DisplayName("SSE convergence harness covers SC-02 through SC-12 source matrix")
  void sse_convergence_harness_covers_sc02_through_sc12_source_matrix() {
    List<Sc02ToSc12BoardConvergenceHarnessRunner.ScenarioProbe> expectedMatrix =
        List.of(
            new Sc02ToSc12BoardConvergenceHarnessRunner.ScenarioProbe(
                "SC-02", "handover_status", "S8", "mock-112-incident-001", true),
            new Sc02ToSc12BoardConvergenceHarnessRunner.ScenarioProbe(
                "SC-03", "package_badge", "S7", "tile-manifest-inc-precinct-001", true),
            new Sc02ToSc12BoardConvergenceHarnessRunner.ScenarioProbe(
                "SC-04", "overall_search_area", "S2", "osa-precinct-001", true),
            new Sc02ToSc12BoardConvergenceHarnessRunner.ScenarioProbe(
                "SC-05", "path", "S3-1", "gps-path-normal-001", true),
            new Sc02ToSc12BoardConvergenceHarnessRunner.ScenarioProbe(
                "SC-06", "marker", "S5", "mk-precinct-clue-001", true),
            new Sc02ToSc12BoardConvergenceHarnessRunner.ScenarioProbe(
                "SC-07", "path", "S6", "net-script-domain-write-001", false),
            new Sc02ToSc12BoardConvergenceHarnessRunner.ScenarioProbe(
                "SC-08", "toast", "S5", "support-request-precinct-001", true),
            new Sc02ToSc12BoardConvergenceHarnessRunner.ScenarioProbe(
                "SC-09", "path", "S4", "sc09OutboxReplayConvergence", true),
            new Sc02ToSc12BoardConvergenceHarnessRunner.ScenarioProbe(
                "SC-10", "op_toggle", "S8", "evt-s8-op-transition-001", true),
            new Sc02ToSc12BoardConvergenceHarnessRunner.ScenarioProbe(
                "SC-11", "search_history_summary", "S8", "ai-summary-op-precinct-001-op2", true),
            new Sc02ToSc12BoardConvergenceHarnessRunner.ScenarioProbe(
                "SC-12", "incident_terminal", "S1-1", "evt-s1-1-incident-closed-001", true));

    List<Sc02ToSc12BoardConvergenceHarnessRunner.ScenarioProbe> observedHarnessMatrix =
        HARNESS.sourceConvergenceMatrix();

    assertThat(observedHarnessMatrix)
        .as("L6-T10B must provide the mocked source contract matrix for SC-02 through SC-12")
        .containsExactlyElementsOf(expectedMatrix);
    assertThat(observedHarnessMatrix)
        .filteredOn(
            probe -> probe.scenarioId().equals("SC-07") && !probe.boardMergeRequired())
        .as("SC-07 is included for task coverage but remains local/offline-only until SC-09 replay")
        .singleElement()
        .satisfies(
            probe -> {
              assertThat(probe.slot()).isEqualTo("path");
              assertThat(probe.sourceFixtureKey()).isEqualTo("net-script-domain-write-001");
            });
  }

  @Test
  @DisplayName("S4/common outbox replay signal converges a path slot probe")
  void s4_common_outbox_replay_signal_converges_path_slot_probe() throws IOException {
    JsonNode s4Fixture =
        fixture("docs/spec/specs/S4.json").at("/harness_fixtures/sc09_outbox_replay_convergence");
    JsonNode commonFixture =
        fixture("docs/spec/fixtures/common-fixtures.json")
            .at("/confirmed/eventFanout/sc09OutboxReplayConvergence");
    JsonNode signalFixture = s4Fixture.at("/expectedRefetchSignal");
    JsonNode boardProbeFixture = s4Fixture.at("/expectedBoardProbe");
    JsonNode commonSignalFixture = commonFixture.at("/expectedRefetchSignal");
    JsonNode commonBoardProbeFixture = commonFixture.at("/expectedBoardProbe");

    assertThat(text(commonFixture, "operationId")).isEqualTo(text(s4Fixture, "operationId"));
    assertThat(text(commonFixture, "idempotencyKey")).isEqualTo(text(s4Fixture, "idempotencyKey"));
    assertThat(text(commonFixture, "eventId")).isEqualTo(text(s4Fixture, "eventId"));
    assertThat(text(commonFixture, "type")).isEqualTo(text(s4Fixture, "type"));
    assertThat(number(commonFixture, "sseSequence")).isEqualTo(number(s4Fixture, "sseSequence"));
    assertThat(text(commonFixture.at("/payload"), "id"))
        .isEqualTo(text(signalFixture, "payloadId"));
    assertThat(text(commonFixture.at("/payload"), "status"))
        .isEqualTo(text(signalFixture, "payloadStatus"));
    assertThat(number(commonFixture.at("/payload"), "version"))
        .isEqualTo(number(signalFixture, "payloadVersion"));
    assertThat(text(commonSignalFixture, "eventId")).isEqualTo(text(signalFixture, "eventId"));
    assertThat(text(commonSignalFixture, "incidentId"))
        .isEqualTo(text(signalFixture, "incidentId"))
        .isEqualTo(text(commonFixture, "incidentId"));
    assertThat(number(commonSignalFixture, "replaySequence"))
        .isEqualTo(number(signalFixture, "replaySequence"))
        .isEqualTo(number(commonFixture, "sseSequence"));
    assertThat(text(commonSignalFixture, "payloadId")).isEqualTo(text(signalFixture, "payloadId"));
    assertThat(text(commonSignalFixture, "payloadStatus"))
        .isEqualTo(text(signalFixture, "payloadStatus"));
    assertThat(number(commonSignalFixture, "payloadVersion"))
        .isEqualTo(number(signalFixture, "payloadVersion"));
    assertThat(text(commonBoardProbeFixture, "latestEventId"))
        .isEqualTo(text(boardProbeFixture, "latestEventId"))
        .isEqualTo(text(commonFixture, "eventId"));
    assertThat(number(commonBoardProbeFixture, "minimumVersion"))
        .isEqualTo(number(boardProbeFixture, "minimumVersion"));

    BoardRefetchSignal signal =
        new BoardRefetchSignal(
            text(signalFixture, "eventId"),
            text(signalFixture, "incidentId"),
            text(commonFixture, "type"),
            OffsetDateTime.parse("2026-04-28T10:30:00+09:00"),
            number(signalFixture, "replaySequence"),
            text(boardProbeFixture, "slot"),
            "S3-1",
            text(signalFixture, "payloadId"),
            text(signalFixture, "payloadStatus"),
            number(signalFixture, "payloadVersion"),
            number(signalFixture, "replaySequence"),
            "hash-s3-path-sc09-outbox-replay",
            Map.of(
                "id", text(signalFixture, "payloadId"),
                "status", text(signalFixture, "payloadStatus"),
                "version", number(signalFixture, "payloadVersion")));

    BoardRefetchResult result =
        new BoardRefetchGuard().apply(staleOutboxPathRequest(signal), List.of(signal));

    assertThat(result.convergenceProbe())
        .as("S4 sc09_outbox_replay_convergence must drive the S3-2 path board probe")
        .satisfies(
            probe -> {
              assertThat(probe.eventId()).isEqualTo(text(signalFixture, "eventId"));
              assertThat(probe.eventId()).isEqualTo(text(boardProbeFixture, "latestEventId"));
              assertThat(probe.slot()).isEqualTo(text(boardProbeFixture, "slot"));
              assertThat(probe.sourceResponseVersion())
                  .isEqualTo(number(boardProbeFixture, "minimumVersion"));
              assertThat(probe.sourceResponseSequence())
                  .isEqualTo(number(signalFixture, "replaySequence"));
              assertThat(probe.boardRowVersion())
                  .isGreaterThanOrEqualTo(number(boardProbeFixture, "minimumVersion"));
              assertThat(probe.boardRowSequence())
                  .isGreaterThanOrEqualTo(number(signalFixture, "replaySequence"));
              assertThat(probe.converged()).isTrue();
            });
  }

  @Test
  @DisplayName("board API refetch lag marks STALE_REFETCH before reload convergence")
  void board_api_refetch_lag_marks_stale_refetch_before_reload_convergence() throws IOException {
    JsonNode delayed =
        fixture("docs/spec/specs/S3-2.json")
            .at("/harness_fixtures/api_assembly_failure_fixtures/delayed_refetch_trigger");
    BoardAssemblyRequest staleBoard = staleAreaRequest(number(delayed, "staleResponseVersion"));
    BoardRefetchSignal sourceSignal =
        new BoardRefetchSignal(
            text(delayed, "eventId"),
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
            "SEARCH_AREA_CHANGED",
            OffsetDateTime.parse("2026-04-28T10:30:00+09:00"),
            number(delayed, "sourceResponseSequence"),
            text(delayed, "slot"),
            "S2",
            text(delayed, "entityId"),
            "ASSIGNED",
            number(delayed, "sourceResponseVersion"),
            number(delayed, "sourceResponseSequence"),
            "hash-s2-area-a1-v3",
            Map.of("id", text(delayed, "entityId"), "status", "ASSIGNED"));

    BoardRefetchGuard guard = new BoardRefetchGuard();
    BoardAssemblyLagState lag = guard.observeAssemblyLag(staleBoard, sourceSignal);
    BoardRefetchResult afterReload = guard.apply(staleBoard, List.of(sourceSignal));

    assertThat(lag.uiState()).isEqualTo(text(delayed, "expectedUiState"));
    assertThat(lag.staleResponseVersion()).isEqualTo(number(delayed, "staleResponseVersion"));
    assertThat(afterReload.convergenceProbe())
        .satisfies(
            probe -> {
              assertThat(probe.boardRowVersion())
                  .isGreaterThanOrEqualTo(number(delayed, "sourceResponseVersion"));
              assertThat(probe.boardRowSequence())
                  .isGreaterThanOrEqualTo(number(delayed, "sourceResponseSequence"));
              assertThat(probe.converged()).isTrue();
            });
  }

  private static void assertCursorPresent(
      String cursorName, Map<String, List<BoardSourceRowCursor>> cursors, BoardSourceRow expected) {
    assertThat(cursors.get(expected.slot()))
        .as(
            "%s row for %s/%s/%s",
            cursorName, expected.slot(), expected.sourceSpec(), expected.sourceResponseId())
        .anySatisfy(cursor -> assertCursor(cursor, expected));
  }

  private static void assertCursor(BoardRowCursor cursor, BoardSourceRow expected) {
    assertThat(cursor.id()).isEqualTo(expected.sourceResponseId());
    assertThat(cursor.status()).isEqualTo(expected.status());
    assertThat(cursor.version()).isEqualTo(expected.version());
    assertThat(cursor.sequence()).isEqualTo(expected.sequence());
    assertThat(cursor.sourceSpec()).isEqualTo(expected.sourceSpec());
    assertThat(cursor.sourceHash()).isEqualTo(expected.sourceHash());
    assertThat(cursor.latestEventId()).isEqualTo(expected.latestEventId());
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> slotRows(BoardDTO board, String slot) {
    return (List<Map<String, Object>>) board.slots().get(slot);
  }

  private static void assertCurrentPathRowUnchanged(BoardDTO board) {
    BoardSlotRow pathRow = board.slotRow("path", "path-precinct-mixed-001");
    assertThat(pathRow.version()).isEqualTo(2);
    assertThat(pathRow.sequence()).isEqualTo(502);
    assertThat(pathRow.latestEventId()).isEqualTo("evt-s3-path-appended-001");
    assertThat(pathRow.sourceHash()).isEqualTo("hash-s3-path-mixed-current");
  }

  private static BoardAssemblyRequest currentMergeRequest() {
    return new BoardAssemblyRequest(
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
        "bs-inc-precinct-first-001",
        1200,
        OffsetDateTime.parse("2026-04-28T10:30:00+09:00"),
        ACTIVE_OP_ID,
        SELECTED_OP_IDS,
        GEOMETRY_HASH,
        List.of(
            pathRow(2, 502, "evt-s3-path-appended-001", "hash-s3-path-mixed-current"),
            toastRow("evt-s5-support-request-001")));
  }

  private static BoardAssemblyRequest staleAreaRequest(long staleVersion) {
    return new BoardAssemblyRequest(
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001",
        "bs-inc-precinct-first-001",
        staleVersion,
        OffsetDateTime.parse("2026-04-28T10:30:00+09:00"),
        ACTIVE_OP_ID,
        SELECTED_OP_IDS,
        GEOMETRY_HASH,
        List.of(areaRow(staleVersion, 402, "evt-s2-area-created-001", "hash-s2-area-a1-current")));
  }

  private static BoardAssemblyRequest staleOutboxPathRequest(BoardRefetchSignal signal) {
    return new BoardAssemblyRequest(
        signal.incidentId(),
        "bs-sc09-outbox-replay-path-001",
        signal.version() - 1,
        signal.serverTs(),
        ACTIVE_OP_ID,
        SELECTED_OP_IDS,
        GEOMETRY_HASH,
        List.of(
            new BoardSourceRow(
                signal.slot(),
                signal.sourceSpec(),
                signal.entityId(),
                "board-path-sc09-outbox-replay",
                "RECORDING",
                signal.version() - 1,
                signal.sequence() - 1,
                "evt-sc09-outbox-before-replay",
                "hash-s3-path-sc09-before-replay",
                Map.of("id", signal.entityId(), "status", "RECORDING"))));
  }

  private static BoardRefetchSignal signal(BoardSourceRow row) {
    String eventType = row.slot().equals("toast") ? "SUPPORT_REQUEST_CREATED" : "PATH_APPENDED";
    return BoardRefetchSignal.fromRow(
        "inc-precinct-first-001",
        eventType,
        OffsetDateTime.parse("2026-04-28T10:30:00+09:00"),
        row);
  }

  private static BoardSourceRow stalePathRow(JsonNode fixture) {
    return pathRow(
        number(fixture, "version"),
        number(fixture, "sequence"),
        text(fixture, "eventId"),
        "hash-s3-path-mixed-stale");
  }

  private static BoardSourceRow missingPredecessorPathRow(JsonNode fixture) {
    return pathRow(
        3, number(fixture, "sequence"), text(fixture, "eventId"), "hash-s3-path-mixed-gap");
  }

  private static BoardSourceRow pathRow(
      long version, long sequence, String eventId, String sourceHash) {
    return new BoardSourceRow(
        "path",
        "S3-1",
        "path-precinct-mixed-001",
        "board-path-precinct-mixed-001",
        "ACTIVE",
        version,
        sequence,
        eventId,
        sourceHash,
        Map.of("id", "path-precinct-mixed-001", "status", "ACTIVE", "version", version));
  }

  private static BoardSourceRow areaRow(
      long version, long sequence, String eventId, String sourceHash) {
    return new BoardSourceRow(
        "area",
        "S2",
        "cccccccc-cccc-cccc-cccc-cccccccc0001",
        "board-area-precinct-a1",
        "ASSIGNED",
        version,
        sequence,
        eventId,
        sourceHash,
        Map.of(
            "id",
            "cccccccc-cccc-cccc-cccc-cccccccc0001",
            "status",
            "ASSIGNED",
            "version",
            version));
  }

  private static BoardSourceRow toastRow(String eventId) {
    return new BoardSourceRow(
        "toast",
        "S5",
        "support-request-precinct-001",
        "board-toast-support-request-precinct-001",
        "VISIBLE",
        1,
        602,
        eventId,
        "hash-s5-toast-support-current",
        Map.of("id", "support-request-precinct-001", "status", "VISIBLE", "version", 1));
  }

  private static JsonNode fixture(String relativePath) throws IOException {
    Path root = repositoryRoot();
    try (var input = Files.newInputStream(root.resolve(relativePath))) {
      return OBJECT_MAPPER.readTree(input);
    }
  }

  private static Path repositoryRoot() {
    Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    if (Files.exists(userDir.resolve("docs/spec/specs/S3-2.json"))) {
      return userDir;
    }
    return userDir.getParent();
  }

  private static String text(JsonNode node, String field) {
    return node.path(field).asText();
  }

  private static long number(JsonNode node, String field) {
    return node.path(field).asLong();
  }
}
