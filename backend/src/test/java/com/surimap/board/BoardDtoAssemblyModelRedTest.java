package com.surimap.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L6-T02A RED: BoardDTO assembly model from source owner response rows. */
@DisplayName("L6-T02A BoardDTO assembly model")
class BoardDtoAssemblyModelRedTest {

  private static final String INCIDENT_ID = "inc-precinct-first-001";
  private static final String BOARD_RESPONSE_ID = "bs-inc-precinct-first-001";
  private static final long BOARD_RESPONSE_VERSION = 1200L;
  private static final OffsetDateTime SERVER_TS = OffsetDateTime.parse("2026-04-28T10:30:00+09:00");
  private static final String ACTIVE_OP_ID = "op-precinct-001-op2";
  private static final List<String> SELECTED_OP_IDS =
      List.of("op-precinct-001-op1", "op-precinct-001-op2");
  private static final String GEOMETRY_HASH = "hash-board-geometry-current";

  @Test
  @DisplayName("assembles BoardDTO cursors without mutating source owner rows")
  void assembles_board_dto_cursors_without_mutating_source_owner_rows() {
    List<OwnerResponseRow> ownerRows = ownerRows();
    List<OwnerResponseRow> beforeAssembly = ownerRows.stream().map(OwnerResponseRow::copy).toList();

    BoardDTO board =
        new BoardAssembler()
            .assemble(
                new BoardAssemblyRequest(
                    INCIDENT_ID,
                    BOARD_RESPONSE_ID,
                    BOARD_RESPONSE_VERSION,
                    SERVER_TS,
                    ACTIVE_OP_ID,
                    SELECTED_OP_IDS,
                    GEOMETRY_HASH,
                    ownerRows.stream().map(BoardDtoAssemblyModelRedTest::sourceRow).toList()));

    assertThat(ownerRows).isEqualTo(beforeAssembly);
    assertThat(board.incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(board.boardResponseVersion()).isEqualTo(BOARD_RESPONSE_VERSION);
    assertThat(board.serverTs()).isEqualTo(SERVER_TS);
    assertThat(board.activeOpId()).isEqualTo(ACTIVE_OP_ID);
    assertThat(board.selectedOpIds()).containsExactlyElementsOf(SELECTED_OP_IDS);
    assertThat(board.geometryHash()).isEqualTo(GEOMETRY_HASH);

    boolean terminalBoard = hasTerminalIncidentRow(ownerRows);
    for (OwnerResponseRow ownerRow : ownerRows) {
      assertThat(board.boardResponseVersion()).isGreaterThanOrEqualTo(ownerRow.version());
      if (isHiddenClosedFreshnessRow(terminalBoard, ownerRow)) {
        continue;
      }
      BoardSlotRow slotRow = board.slotRow(ownerRow.slot(), ownerRow.sourceResponseId());
      assertCursor(slotRow, ownerRow);
      assertThat(slotRow.boardRowId()).isEqualTo(ownerRow.boardRowId());
      assertThat(slotRow.payload()).containsAllEntriesOf(expectedResponsePayload(ownerRow));
      assertSanitizedTerminalPayload(slotRow.responseFields(), ownerRow);
      assertFlattenedSlotShape(board, ownerRow);
      assertCursor(singleCursor(board.slotSources(), ownerRow), ownerRow);
      assertCursor(singleCursor(board.sourceVersions(), ownerRow), ownerRow);
      assertCursor(singleCursor(board.sourceHashes(), ownerRow), ownerRow);
    }
  }

  @Test
  @DisplayName("hides police_phone_freshness rows when incident terminal is closed")
  void hides_police_phone_freshness_rows_when_incident_terminal_is_closed() {
    BoardDTO board =
        new BoardAssembler()
            .assemble(
                new BoardAssemblyRequest(
                    INCIDENT_ID,
                    BOARD_RESPONSE_ID,
                    BOARD_RESPONSE_VERSION,
                    SERVER_TS,
                    ACTIVE_OP_ID,
                    SELECTED_OP_IDS,
                    GEOMETRY_HASH,
                    ownerRows().stream().map(BoardDtoAssemblyModelRedTest::sourceRow).toList()));

    assertThat(board.slots().get("police_phone_freshness")).isEqualTo(List.of());
    assertThat(board.slotSources().get("police_phone_freshness")).isEmpty();
    assertThat(board.sourceVersions().get("police_phone_freshness")).isEmpty();
    assertThat(board.sourceHashes().get("police_phone_freshness")).isEmpty();
    assertThatThrownBy(() -> board.slotRow("police_phone_freshness", "dev-precinct-car-01"))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("sanitizes incident_terminal payload to tombstone fields only")
  void sanitizes_incident_terminal_payload_to_tombstone_fields_only() {
    OwnerResponseRow terminalRow =
        ownerRows().stream()
            .filter(row -> row.slot().equals("incident_terminal"))
            .findFirst()
            .orElseThrow();

    BoardDTO board =
        new BoardAssembler()
            .assemble(
                new BoardAssemblyRequest(
                    INCIDENT_ID,
                    BOARD_RESPONSE_ID,
                    BOARD_RESPONSE_VERSION,
                    SERVER_TS,
                    ACTIVE_OP_ID,
                    SELECTED_OP_IDS,
                    GEOMETRY_HASH,
                    List.of(sourceRow(terminalRow))));

    @SuppressWarnings("unchecked")
    Map<String, Object> terminalSlot = (Map<String, Object>) board.slots().get("incident_terminal");
    assertSanitizedTerminalPayload(terminalSlot, terminalRow);
    assertSanitizedTerminalPayload(
        board.slotRow("incident_terminal", INCIDENT_ID).responseFields(), terminalRow);
  }

  @Test
  @DisplayName("rejects invalid incident_terminal enum values before exposing freshness")
  void rejects_invalid_incident_terminal_enum_values_before_exposing_freshness() {
    OwnerResponseRow terminalRow =
        ownerRows().stream()
            .filter(row -> row.slot().equals("incident_terminal"))
            .findFirst()
            .orElseThrow();
    Map<String, Object> invalidPayload = new LinkedHashMap<>(terminalRow.payload());
    invalidPayload.put("terminalStatus", "INVALID");
    OwnerResponseRow invalidTerminalRow = terminalRow.withPayload(invalidPayload);
    OwnerResponseRow freshnessRow =
        ownerRows().stream()
            .filter(row -> row.slot().equals("police_phone_freshness"))
            .findFirst()
            .orElseThrow();

    assertThatThrownBy(
            () ->
                new BoardAssembler()
                    .assemble(
                        new BoardAssemblyRequest(
                            INCIDENT_ID,
                            BOARD_RESPONSE_ID,
                            BOARD_RESPONSE_VERSION,
                            SERVER_TS,
                            ACTIVE_OP_ID,
                            SELECTED_OP_IDS,
                            GEOMETRY_HASH,
                            List.of(sourceRow(freshnessRow), sourceRow(invalidTerminalRow)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid incident_terminal terminalStatus");
  }

  @Test
  @DisplayName("rejects closed incident_terminal rows without closedAt")
  void rejects_closed_incident_terminal_rows_without_closed_at() {
    OwnerResponseRow terminalRow =
        ownerRows().stream()
            .filter(row -> row.slot().equals("incident_terminal"))
            .findFirst()
            .orElseThrow();
    Map<String, Object> invalidPayload = new LinkedHashMap<>(terminalRow.payload());
    invalidPayload.put("closedAt", null);

    assertThatThrownBy(
            () ->
                new BoardAssembler()
                    .assemble(
                        new BoardAssemblyRequest(
                            INCIDENT_ID,
                            BOARD_RESPONSE_ID,
                            BOARD_RESPONSE_VERSION,
                            SERVER_TS,
                            ACTIVE_OP_ID,
                            SELECTED_OP_IDS,
                            GEOMETRY_HASH,
                            List.of(sourceRow(terminalRow.withPayload(invalidPayload))))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("closedAt is required");
  }

  @Test
  @DisplayName("advances boardResponseVersion when an owner row version exceeds the base")
  void advances_board_response_version_when_owner_row_version_exceeds_base() {
    long ownerVersionAboveBase = BOARD_RESPONSE_VERSION + 7;
    OwnerResponseRow newerOwnerRow =
        row(
            "area",
            "S2",
            "area-precinct-a2",
            "board-area-precinct-a2",
            "ASSIGNED",
            ownerVersionAboveBase,
            405,
            "evt-s2-area-created-002",
            "hash-s2-area-a2-current");

    BoardDTO board =
        new BoardAssembler()
            .assemble(
                new BoardAssemblyRequest(
                    INCIDENT_ID,
                    BOARD_RESPONSE_ID,
                    BOARD_RESPONSE_VERSION,
                    SERVER_TS,
                    ACTIVE_OP_ID,
                    SELECTED_OP_IDS,
                    GEOMETRY_HASH,
                    List.of(sourceRow(newerOwnerRow))));

    assertThat(board.boardResponseVersion()).isEqualTo(ownerVersionAboveBase);
  }

  @Test
  @DisplayName("rejects unknown board slot keys before assembly")
  void rejects_unknown_board_slot_keys_before_assembly() {
    BoardSourceRow unknownSlotRow =
        new BoardSourceRow(
            "invented_slot",
            "S3-2",
            "invented-row-001",
            "board-invented-row-001",
            "ACTIVE",
            1,
            1,
            "evt-invented-row-001",
            "hash-invented-row",
            Map.of("id", "invented-row-001"));

    assertThatThrownBy(
            () ->
                new BoardAssembler()
                    .assemble(
                        new BoardAssemblyRequest(
                            INCIDENT_ID,
                            BOARD_RESPONSE_ID,
                            BOARD_RESPONSE_VERSION,
                            SERVER_TS,
                            ACTIVE_OP_ID,
                            SELECTED_OP_IDS,
                            GEOMETRY_HASH,
                            List.of(unknownSlotRow))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown board slot");
  }

  @SuppressWarnings("unchecked")
  private static void assertFlattenedSlotShape(BoardDTO board, OwnerResponseRow ownerRow) {
    Object slotValue = board.slots().get(ownerRow.slot());
    Map<String, Object> rowObject;
    if (BoardSlotRegistry.isSingletonSlot(ownerRow.slot())) {
      assertThat(slotValue).isInstanceOf(Map.class);
      rowObject = (Map<String, Object>) slotValue;
    } else {
      assertThat(slotValue).isInstanceOf(List.class);
      rowObject =
          ((List<Map<String, Object>>) slotValue)
              .stream()
                  .filter(row -> row.get("id").equals(ownerRow.sourceResponseId()))
                  .findFirst()
                  .orElseThrow();
    }

    assertThat(rowObject)
        .containsEntry("id", ownerRow.sourceResponseId())
        .containsEntry("status", ownerRow.status())
        .containsEntry("version", ownerRow.version())
        .containsEntry("sequence", ownerRow.sequence())
        .containsEntry("sourceSpec", ownerRow.sourceSpec())
        .containsEntry("sourceHash", ownerRow.sourceHash())
        .containsEntry("latestEventId", ownerRow.latestEventId())
        .containsAllEntriesOf(expectedResponsePayload(ownerRow))
        .doesNotContainKey("payload");
    assertSanitizedTerminalPayload(rowObject, ownerRow);
    String[] requiredPayloadKeys = requiredPayloadKeys(ownerRow.slot());
    if (requiredPayloadKeys.length > 0) {
      assertThat(rowObject).containsKeys(requiredPayloadKeys);
    }
  }

  private static String[] requiredPayloadKeys(String slot) {
    return switch (slot) {
      case "overall_search_area" -> new String[] {"geometry", "geometryHash"};
      case "area" -> new String[] {"opId", "geometry", "geometryHash"};
      case "path" -> new String[] {"opId", "policePhoneId", "geometry", "geometryHash"};
      case "police_phone_freshness" ->
          new String[] {
            "policePhoneId", "freshness", "lastHeartbeatAt", "lastSyncAt", "elapsedSeconds"
          };
      case "marker" -> new String[] {"opId", "geometry", "geometryHash"};
      case "toast" -> new String[] {"markerId"};
      case "package_badge" -> new String[] {"policePhoneId", "policePhoneCode", "policePhoneName"};
      case "op_toggle", "op_history", "handover_status" -> new String[] {};
      case "handover_memo" -> new String[] {"opId"};
      case "search_history_summary" -> new String[] {"opId", "summaryText"};
      case "incident_terminal" ->
          new String[] {
            "incidentId",
            "terminalStatus",
            "closedStatus",
            "closedAt",
            "writeDisabledReason",
            "localPurgeState"
          };
      default -> throw new IllegalArgumentException("unknown test slot: " + slot);
    };
  }

  private static Map<String, Object> expectedResponsePayload(OwnerResponseRow ownerRow) {
    if (!ownerRow.slot().equals("incident_terminal")) {
      return ownerRow.payload();
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    for (String key : terminalPayloadKeys()) {
      payload.put(key, ownerRow.payload().get(key));
    }
    return payload;
  }

  private static void assertSanitizedTerminalPayload(
      Map<String, Object> rowObject, OwnerResponseRow ownerRow) {
    if (!ownerRow.slot().equals("incident_terminal")) {
      return;
    }
    assertThat(rowObject)
        .containsEntry("closedAt", "2026-04-28T10:29:58+09:00")
        .doesNotContainKeys(
            "missingPersonName", "latestLocation", "packageReloadUrl", "streamResubscribeHint");
    assertThat(terminalStatusValues()).contains(String.valueOf(rowObject.get("terminalStatus")));
    assertThat(closedStatusValues()).contains(String.valueOf(rowObject.get("closedStatus")));
    assertThat(writeDisabledReasonValues())
        .contains(String.valueOf(rowObject.get("writeDisabledReason")));
    assertThat(localPurgeStateValues()).contains(String.valueOf(rowObject.get("localPurgeState")));
    assertThat(rowObject.keySet()).containsExactlyInAnyOrderElementsOf(terminalResponseKeys());
  }

  private static List<String> terminalPayloadKeys() {
    return List.of(
        "incidentId",
        "terminalStatus",
        "closedStatus",
        "closedAt",
        "writeDisabledReason",
        "localPurgeState");
  }

  private static List<String> terminalStatusValues() {
    return List.of("OPEN", "CLOSED", "PURGE_PENDING", "PURGED");
  }

  private static List<String> closedStatusValues() {
    return List.of("not_closed", "closed", "purge_pending", "purged");
  }

  private static List<String> writeDisabledReasonValues() {
    return List.of("none", "incident_closed", "purged");
  }

  private static List<String> localPurgeStateValues() {
    return List.of("not_started", "queued", "in_progress", "completed", "failed_retryable");
  }

  private static List<String> terminalResponseKeys() {
    return List.of(
        "id",
        "status",
        "version",
        "sequence",
        "sourceSpec",
        "sourceHash",
        "latestEventId",
        "incidentId",
        "terminalStatus",
        "closedStatus",
        "closedAt",
        "writeDisabledReason",
        "localPurgeState");
  }

  private static BoardSourceRowCursor singleCursor(
      Map<String, List<BoardSourceRowCursor>> cursorsBySlot, OwnerResponseRow ownerRow) {
    assertThat(cursorsBySlot).containsKey(ownerRow.slot());

    List<BoardSourceRowCursor> cursors = cursorsBySlot.get(ownerRow.slot());
    assertThat(cursors)
        .filteredOn(cursor -> cursor.id().equals(ownerRow.sourceResponseId()))
        .singleElement();

    return cursors.stream()
        .filter(cursor -> cursor.id().equals(ownerRow.sourceResponseId()))
        .findFirst()
        .orElseThrow();
  }

  private static void assertCursor(BoardRowCursor actual, OwnerResponseRow expected) {
    assertThat(actual.id()).isEqualTo(expected.sourceResponseId());
    assertThat(actual.status()).isEqualTo(expected.status());
    assertThat(actual.version()).isEqualTo(expected.version());
    assertThat(actual.sequence()).isEqualTo(expected.sequence());
    assertThat(actual.sourceSpec()).isEqualTo(expected.sourceSpec());
    assertThat(actual.sourceHash()).isEqualTo(expected.sourceHash());
    assertThat(actual.latestEventId()).isEqualTo(expected.latestEventId());
  }

  private static BoardSourceRow sourceRow(OwnerResponseRow ownerRow) {
    return new BoardSourceRow(
        ownerRow.slot(),
        ownerRow.sourceSpec(),
        ownerRow.sourceResponseId(),
        ownerRow.boardRowId(),
        ownerRow.status(),
        ownerRow.version(),
        ownerRow.sequence(),
        ownerRow.latestEventId(),
        ownerRow.sourceHash(),
        ownerRow.payload());
  }

  private static boolean hasTerminalIncidentRow(List<OwnerResponseRow> ownerRows) {
    return ownerRows.stream()
        .anyMatch(
            row ->
                row.slot().equals("incident_terminal")
                    && List.of("CLOSED", "PURGE_PENDING", "PURGED")
                        .contains(String.valueOf(row.payload().get("terminalStatus"))));
  }

  private static boolean isHiddenClosedFreshnessRow(
      boolean terminalBoard, OwnerResponseRow ownerRow) {
    return terminalBoard && ownerRow.slot().equals("police_phone_freshness");
  }

  private static List<OwnerResponseRow> ownerRows() {
    return List.of(
        row(
            "overall_search_area",
            "S2",
            "osa-precinct-001",
            "board-overall-search-area-inc-precinct-first-001",
            "ACTIVE",
            2,
            401,
            "evt-s2-overall-area-001",
            "hash-s2-overall-area-current"),
        row(
            "area",
            "S2",
            "area-precinct-a1",
            "board-area-precinct-a1",
            "ASSIGNED",
            1,
            402,
            "evt-s2-area-created-001",
            "hash-s2-area-a1-current"),
        row(
            "path",
            "S3-1",
            "path-precinct-mixed-001",
            "board-path-precinct-mixed-001",
            "ACTIVE",
            2,
            502,
            "evt-s3-path-appended-001",
            "hash-s3-path-mixed-current"),
        row(
            "police_phone_freshness",
            "S1-2",
            "dev-precinct-car-01",
            "board-PolicePhone-freshness-dev-precinct-car-01",
            "ONLINE",
            4,
            504,
            "evt-s1-2-heartbeat-001",
            "hash-s1-2-dev-precinct-car-current"),
        row(
            "marker",
            "S5",
            "mk-precinct-clue-001",
            "board-marker-mk-precinct-clue-001",
            "ACTIVE",
            1,
            601,
            "evt-s5-marker-created-001",
            "hash-s5-marker-clue-current"),
        row(
            "toast",
            "S5",
            "support-request-precinct-001",
            "board-toast-support-request-precinct-001",
            "VISIBLE",
            1,
            602,
            "evt-s5-support-request-001",
            "hash-s5-toast-support-current"),
        row(
            "package_badge",
            "S7",
            "pkg-inc-precinct-first-001",
            "board-package-inc-precinct-first-001",
            "STALE",
            3,
            701,
            "evt-s7-package-stale-001",
            "hash-s7-package-current"),
        row(
            "op_toggle",
            "S8",
            "op-precinct-001-op1",
            "board-op-toggle-op-precinct-001-op1",
            "ACTIVE",
            1,
            801,
            "evt-s8-op-transition-001",
            "hash-s8-op1-current"),
        row(
            "op_history",
            "S8",
            "op-precinct-001-op2",
            "board-op-history-op-precinct-001-op2",
            "OPENED",
            2,
            802,
            "evt-s8-op-transition-001",
            "hash-s8-op2-current"),
        row(
            "handover_memo",
            "S8",
            "memo-precinct-handover-001",
            "board-handover-memo-precinct-001",
            "ACTIVE",
            1,
            803,
            "evt-s8-handover-created-001",
            "hash-s8-handover-memo-current"),
        row(
            "handover_status",
            "S8",
            "handover-status-inc-precinct-first-001",
            "board-handover-status-inc-precinct-first-001",
            "READY",
            2,
            804,
            "evt-s8-handover-created-001",
            "hash-s8-handover-status-current"),
        row(
            "search_history_summary",
            "S8",
            "ai-summary-op-precinct-001-op2",
            "board-ai-summary-op-precinct-001-op2",
            "READY",
            1,
            805,
            "evt-s8-ai-summary-ready-001",
            "hash-s8-ai-summary-current"),
        row(
            "incident_terminal",
            "S1-1",
            "inc-precinct-first-001",
            "board-incident-terminal-inc-precinct-first-001",
            "CLOSED",
            9,
            901,
            "evt-s1-1-incident-closed-001",
            "hash-s1-terminal-current"));
  }

  private static OwnerResponseRow row(
      String slot,
      String sourceSpec,
      String sourceResponseId,
      String boardRowId,
      String status,
      long version,
      long sequence,
      String latestEventId,
      String sourceHash) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.putAll(sourceSpecificPayload(slot, sourceResponseId, status));
    return new OwnerResponseRow(
        slot,
        sourceSpec,
        sourceResponseId,
        boardRowId,
        status,
        version,
        sequence,
        latestEventId,
        sourceHash,
        payload);
  }

  private static Map<String, Object> sourceSpecificPayload(
      String slot, String sourceResponseId, String status) {
    Map<String, Object> payload = new LinkedHashMap<>();
    switch (slot) {
      case "overall_search_area" -> {
        payload.put("geometryHash", "hash-geometry-" + sourceResponseId);
        payload.put("geometry", Map.of("type", "Polygon"));
      }
      case "area" -> {
        payload.put("opId", ACTIVE_OP_ID);
        payload.put("geometryHash", "hash-geometry-" + sourceResponseId);
        payload.put("geometry", Map.of("type", "Polygon"));
      }
      case "path" -> {
        payload.put("opId", ACTIVE_OP_ID);
        payload.put("policePhoneId", "dev-precinct-car-01");
        payload.put("geometryHash", "hash-geometry-" + sourceResponseId);
        payload.put("geometry", Map.of("type", "LineString"));
      }
      case "police_phone_freshness" -> {
        payload.put("policePhoneId", sourceResponseId);
        payload.put("freshness", "normal");
        payload.put("lastHeartbeatAt", "2026-04-28T10:29:30+09:00");
        payload.put("lastSyncAt", "2026-04-28T10:29:35+09:00");
        payload.put("elapsedSeconds", 30);
      }
      case "marker" -> {
        payload.put("opId", ACTIVE_OP_ID);
        payload.put("markerType", "CLUE");
        payload.put("geometryHash", "hash-geometry-" + sourceResponseId);
        payload.put("geometry", Map.of("type", "Point"));
      }
      case "toast" -> {
        payload.put("markerId", "mk-precinct-support-001");
        payload.put("messageKey", "support_request_created");
      }
      case "package_badge" -> {
        payload.put("policePhoneId", "00000000-0000-0000-0000-000000000101");
        payload.put("policePhoneCode", "dev-precinct-phone-01");
        payload.put("policePhoneName", "경찰서 팀폰");
        payload.put("packageStatus", status);
      }
      case "op_toggle", "op_history" -> payload.put("opId", sourceResponseId);
      case "handover_memo" -> payload.put("opId", ACTIVE_OP_ID);
      case "handover_status" -> payload.put("handoverStatus", status);
      case "search_history_summary" -> {
        payload.put("opId", ACTIVE_OP_ID);
        payload.put("summaryText", "OP history summary fixture");
      }
      case "incident_terminal" -> {
        payload.put("incidentId", INCIDENT_ID);
        payload.put("terminalStatus", status);
        payload.put("closedStatus", "closed");
        payload.put("closedAt", "2026-04-28T10:29:58+09:00");
        payload.put("writeDisabledReason", "incident_closed");
        payload.put("localPurgeState", "queued");
        payload.put("missingPersonName", "fixture-person-name");
        payload.put("latestLocation", Map.of("type", "Point"));
        payload.put("packageReloadUrl", "/api/incidents/" + INCIDENT_ID + "/packages/reload");
        payload.put("streamResubscribeHint", "/api/incidents/" + INCIDENT_ID + "/events");
      }
      default -> throw new IllegalArgumentException("unknown test slot: " + slot);
    }
    return payload;
  }

  record OwnerResponseRow(
      String slot,
      String sourceSpec,
      String sourceResponseId,
      String boardRowId,
      String status,
      long version,
      long sequence,
      String latestEventId,
      String sourceHash,
      Map<String, Object> payload) {

    OwnerResponseRow copy() {
      return new OwnerResponseRow(
          slot,
          sourceSpec,
          sourceResponseId,
          boardRowId,
          status,
          version,
          sequence,
          latestEventId,
          sourceHash,
          new LinkedHashMap<>(payload));
    }

    OwnerResponseRow withPayload(Map<String, Object> payload) {
      return new OwnerResponseRow(
          slot,
          sourceSpec,
          sourceResponseId,
          boardRowId,
          status,
          version,
          sequence,
          latestEventId,
          sourceHash,
          new LinkedHashMap<>(payload));
    }
  }
}
