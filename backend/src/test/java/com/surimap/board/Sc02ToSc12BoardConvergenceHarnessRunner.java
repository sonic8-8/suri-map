package com.surimap.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class Sc02ToSc12BoardConvergenceHarnessRunner {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String ACTIVE_OP_ID = "op-precinct-001-op2";
  private static final List<String> SELECTED_OP_IDS =
      List.of("op-precinct-001-op1", "op-precinct-001-op2");
  private static final String GEOMETRY_HASH = "hash-board-geometry-current";
  private static final List<String> TASK_SCENARIO_MATRIX =
      List.of(
          "SC-02", "SC-03", "SC-04", "SC-05", "SC-06", "SC-07", "SC-08", "SC-09", "SC-10",
          "SC-11", "SC-12");

  BoardApiAssemblyCoverageReport boardApiAssemblyCoverage() throws IOException {
    JsonNode s32 = fixture("docs/spec/specs/S3-2.json");
    JsonNode base = s32.at("/harness_fixtures/board_api_base");
    List<BoardSourceRow> sourceRows = s32SlotRows(s32);
    BoardDTO board =
        new BoardAssembler()
            .assemble(
                new BoardAssemblyRequest(
                    text(base, "incidentId"),
                    text(base, "boardResponseId"),
                    number(base, "boardResponseVersion"),
                    OffsetDateTime.parse(text(base, "serverTs")),
                    ACTIVE_OP_ID,
                    SELECTED_OP_IDS,
                    GEOMETRY_HASH,
                    sourceRows));

    return new BoardApiAssemblyCoverageReport(
        board,
        sourceRows,
        sourceCoverage(sourceRows),
        sourceCoverage(sourceRows),
        sourceCoverage(sourceRows),
        hiddenClosedIncidentFreshnessRows(sourceRows));
  }

  List<ScenarioProbe> sourceConvergenceMatrix() {
    return TASK_SCENARIO_MATRIX.stream().map(this::scenarioProbe).toList();
  }

  private ScenarioProbe scenarioProbe(String scenarioId) {
    return switch (scenarioId) {
      case "SC-02" ->
          new ScenarioProbe(
              scenarioId, "handover_status", "S8", "mock-112-incident-001", true);
      case "SC-03" ->
          new ScenarioProbe(
              scenarioId,
              "package_badge",
              "S7",
              "tile-manifest-inc-precinct-001",
              true);
      case "SC-04" ->
          new ScenarioProbe(
              scenarioId, "overall_search_area", "S2", "osa-precinct-001", true);
      case "SC-05" ->
          new ScenarioProbe(scenarioId, "path", "S3-1", "gps-path-normal-001", true);
      case "SC-06" ->
          new ScenarioProbe(scenarioId, "marker", "S5", "mk-precinct-clue-001", true);
      case "SC-07" ->
          new ScenarioProbe(scenarioId, "path", "S6", "net-script-domain-write-001", false);
      case "SC-08" ->
          new ScenarioProbe(scenarioId, "toast", "S5", "support-request-precinct-001", true);
      case "SC-09" ->
          new ScenarioProbe(scenarioId, "path", "S4", "sc09OutboxReplayConvergence", true);
      case "SC-10" ->
          new ScenarioProbe(scenarioId, "op_toggle", "S8", "evt-s8-op-transition-001", true);
      case "SC-11" ->
          new ScenarioProbe(
              scenarioId,
              "search_history_summary",
              "S8",
              "ai-summary-op-precinct-001-op2",
              true);
      case "SC-12" ->
          new ScenarioProbe(
              scenarioId,
              "incident_terminal",
              "S1-1",
              "evt-s1-1-incident-closed-001",
              true);
      default -> throw new IllegalArgumentException("unknown scenario id: " + scenarioId);
    };
  }

  private static Map<String, List<BoardSourceRowCursor>> sourceCoverage(
      List<BoardSourceRow> sourceRows) {
    Map<String, List<BoardSourceRowCursor>> coverage = emptyCursorMap();
    for (BoardSourceRow row : sourceRows) {
      coverage.get(row.slot()).add(BoardSourceRowCursor.from(row));
    }
    return freeze(coverage);
  }

  private static Map<SourceRowKey, String> hiddenClosedIncidentFreshnessRows(
      List<BoardSourceRow> sourceRows) {
    boolean terminalBoard = sourceRows.stream().anyMatch(BoardSlotRow::isTerminalIncidentRow);
    Map<SourceRowKey, String> hiddenRows = new LinkedHashMap<>();
    for (BoardSourceRow row : sourceRows) {
      if (terminalBoard && row.slot().equals("police_phone_freshness")) {
        hiddenRows.put(SourceRowKey.from(row), "closed_incident_hides_police_phone_freshness");
      }
    }
    return Collections.unmodifiableMap(hiddenRows);
  }

  private static Map<String, List<BoardSourceRowCursor>> emptyCursorMap() {
    Map<String, List<BoardSourceRowCursor>> cursors = new LinkedHashMap<>();
    for (String slot : BoardSlotRegistry.requiredSlots()) {
      cursors.put(slot, new ArrayList<>());
    }
    return cursors;
  }

  private static Map<String, List<BoardSourceRowCursor>> freeze(
      Map<String, List<BoardSourceRowCursor>> source) {
    Map<String, List<BoardSourceRowCursor>> frozen = new LinkedHashMap<>();
    for (Map.Entry<String, List<BoardSourceRowCursor>> entry : source.entrySet()) {
      frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    return Collections.unmodifiableMap(frozen);
  }

  private static List<BoardSourceRow> s32SlotRows(JsonNode s32) {
    List<BoardSourceRow> rows = new ArrayList<>();
    for (JsonNode row : s32.at("/harness_fixtures/slot_rows")) {
      rows.add(sourceRow(row));
    }
    return rows;
  }

  private static BoardSourceRow sourceRow(JsonNode row) {
    String slot = text(row, "slot");
    return new BoardSourceRow(
        slot,
        text(row, "sourceSpec"),
        text(row, "sourceResponseId"),
        text(row, "boardRowId"),
        text(row, "status"),
        number(row, "version"),
        number(row, "sequence"),
        text(row, "latestEventId"),
        text(row, "sourceHash"),
        payloadFor(row));
  }

  private static Map<String, Object> payloadFor(JsonNode row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", text(row, "sourceResponseId"));
    payload.put("status", text(row, "status"));
    payload.put("version", number(row, "version"));
    if (text(row, "slot").equals("incident_terminal")) {
      payload.put("incidentId", text(row, "sourceResponseId"));
      payload.put("terminalStatus", text(row, "status"));
      payload.put("closedStatus", "closed");
      payload.put("closedAt", "2026-04-28T10:30:00+09:00");
      payload.put("writeDisabledReason", "incident_closed");
      payload.put("localPurgeState", "queued");
    }
    return payload;
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

  record BoardApiAssemblyCoverageReport(
      BoardDTO board,
      List<BoardSourceRow> sourceRows,
      Map<String, List<BoardSourceRowCursor>> slotSources,
      Map<String, List<BoardSourceRowCursor>> sourceVersions,
      Map<String, List<BoardSourceRowCursor>> sourceHashes,
      Map<SourceRowKey, String> hiddenVisibleRows) {

    BoardApiAssemblyCoverageReport {
      sourceRows = List.copyOf(sourceRows);
      slotSources = freeze(slotSources);
      sourceVersions = freeze(sourceVersions);
      sourceHashes = freeze(sourceHashes);
      hiddenVisibleRows = Collections.unmodifiableMap(new LinkedHashMap<>(hiddenVisibleRows));
    }

    boolean visibleInBoard(BoardSourceRow row) {
      return !hiddenVisibleRows.containsKey(SourceRowKey.from(row));
    }

    Optional<String> hiddenReason(BoardSourceRow row) {
      return Optional.ofNullable(hiddenVisibleRows.get(SourceRowKey.from(row)));
    }
  }

  record ScenarioProbe(
      String scenarioId,
      String slot,
      String sourceSpec,
      String sourceFixtureKey,
      boolean boardMergeRequired) {

    ScenarioProbe {
      BoardSlotRegistry.requireKnown(slot);
    }
  }

  record SourceRowKey(String slot, String sourceSpec, String sourceResponseId) {

    static SourceRowKey from(BoardSourceRow row) {
      return new SourceRowKey(row.slot(), row.sourceSpec(), row.sourceResponseId());
    }
  }
}
