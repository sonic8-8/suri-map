package com.surimap.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L6-T02B RED: eventId dedupe and stale refetch guard for BoardDTO assembly. */
@DisplayName("L6-T02B board refetch guard")
class BoardRefetchGuardRedTest {

  private static final String INCIDENT_ID = "inc-precinct-first-001";
  private static final String BOARD_RESPONSE_ID = "bs-inc-precinct-first-001";
  private static final long BOARD_RESPONSE_VERSION = 1200L;
  private static final OffsetDateTime SERVER_TS = OffsetDateTime.parse("2026-04-28T10:30:00+09:00");
  private static final String ACTIVE_OP_ID = "op-precinct-001-op2";
  private static final List<String> SELECTED_OP_IDS =
      List.of("op-precinct-001-op1", "op-precinct-001-op2");
  private static final String GEOMETRY_HASH = "hash-board-geometry-current";

  @Test
  @DisplayName("deduplicates duplicate eventId without adding another board row")
  void deduplicates_duplicate_event_id_without_adding_another_board_row() {
    BoardRefetchResult result =
        new BoardRefetchGuard().apply(currentRequest(), List.of(signal(duplicateToastEvent())));

    assertThat(result.ledger())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.eventId()).isEqualTo("evt-s5-support-request-001");
              assertThat(entry.incidentId()).isEqualTo(INCIDENT_ID);
              assertThat(entry.applyStatus()).isEqualTo(BoardRefetchLedgerStatus.DUPLICATE);
              assertThat(entry.slot()).isEqualTo("toast");
              assertThat(entry.entityId()).isEqualTo("support-request-precinct-001");
              assertThat(entry.status()).isEqualTo("VISIBLE");
              assertThat(entry.version()).isEqualTo(1);
              assertThat(entry.sequence()).isEqualTo(602);
              assertThat(entry.sourceHash()).isEqualTo("hash-s5-toast-support-current");
              assertThat(entry.boardResponseVersion())
                  .isEqualTo(result.board().boardResponseVersion());
            });
    assertThat(toastRows(result.board())).hasSize(1);
    assertThat(toastRows(result.board()).get(0).get("latestEventId"))
        .isEqualTo("evt-s5-support-request-001");
  }

  @Test
  @DisplayName("ignores stale lower version and sequence for the same slot source entity")
  void ignores_stale_lower_version_and_sequence_for_same_slot_source_entity() {
    BoardRefetchResult result =
        new BoardRefetchGuard().apply(currentRequest(), List.of(signal(stalePathEvent())));

    assertThat(result.ledger())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.eventId()).isEqualTo("evt-s3-path-appended-stale-001");
              assertThat(entry.applyStatus()).isEqualTo(BoardRefetchLedgerStatus.STALE);
              assertThat(entry.previousVersion()).isEqualTo(2);
              assertThat(entry.version()).isEqualTo(1);
              assertThat(entry.previousSequence()).isEqualTo(502);
              assertThat(entry.sequence()).isEqualTo(501);
            });

    BoardSlotRow pathRow = result.board().slotRow("path", "path-precinct-mixed-001");
    assertThat(pathRow.version()).isEqualTo(2);
    assertThat(pathRow.sequence()).isEqualTo(502);
    assertThat(pathRow.sourceHash()).isEqualTo("hash-s3-path-mixed-current");
    assertThat(pathRow.latestEventId()).isEqualTo("evt-s3-path-appended-001");
  }

  @Test
  @DisplayName("marks sequence gaps as refetch required without applying the row")
  void marks_sequence_gaps_as_refetch_required_without_applying_the_row() {
    BoardRefetchResult result =
        new BoardRefetchGuard()
            .apply(currentRequest(), List.of(signal(missingPredecessorPathEvent())));

    assertThat(result.ledger())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.eventId()).isEqualTo("evt-s3-path-appended-gap-003");
              assertThat(entry.applyStatus()).isEqualTo(BoardRefetchLedgerStatus.REFETCH_REQUIRED);
              assertThat(entry.reloadReason()).isEqualTo(BoardReloadReason.MISSING_PREDECESSOR);
              assertThat(entry.previousSequence()).isEqualTo(502);
              assertThat(entry.sequence()).isEqualTo(505);
            });

    BoardSlotRow pathRow = result.board().slotRow("path", "path-precinct-mixed-001");
    assertThat(pathRow.version()).isEqualTo(2);
    assertThat(pathRow.sequence()).isEqualTo(502);
    assertThat(pathRow.latestEventId()).isEqualTo("evt-s3-path-appended-001");
  }

  @Test
  @DisplayName("applies only newer owner versions to the assembled board response")
  void applies_only_newer_owner_versions_to_the_assembled_board_response() {
    BoardRefetchResult result =
        new BoardRefetchGuard().apply(currentRequest(), List.of(signal(newerPathEvent())));

    assertThat(result.ledger())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.eventId()).isEqualTo("evt-s3-path-appended-002");
              assertThat(entry.applyStatus()).isEqualTo(BoardRefetchLedgerStatus.APPLIED);
              assertThat(entry.previousVersion()).isEqualTo(2);
              assertThat(entry.version()).isEqualTo(3);
            });

    BoardSlotRow pathRow = result.board().slotRow("path", "path-precinct-mixed-001");
    assertThat(pathRow.version()).isEqualTo(3);
    assertThat(pathRow.sequence()).isEqualTo(503);
    assertThat(pathRow.sourceHash()).isEqualTo("hash-s3-path-mixed-v3");
    assertThat(pathRow.latestEventId()).isEqualTo("evt-s3-path-appended-002");
  }

  @Test
  @DisplayName("keeps latest owner version when events are reordered")
  void keeps_latest_owner_version_when_events_are_reordered() {
    BoardRefetchResult result =
        new BoardRefetchGuard()
            .apply(currentRequest(), List.of(signal(newerPathEvent()), signal(stalePathEvent())));

    assertThat(result.ledger())
        .extracting(BoardRefetchLedgerEntry::applyStatus)
        .containsExactly(BoardRefetchLedgerStatus.APPLIED, BoardRefetchLedgerStatus.STALE);

    BoardSlotRow pathRow = result.board().slotRow("path", "path-precinct-mixed-001");
    assertThat(pathRow.version()).isEqualTo(3);
    assertThat(pathRow.sequence()).isEqualTo(503);
    assertThat(pathRow.latestEventId()).isEqualTo("evt-s3-path-appended-002");
  }

  @Test
  @DisplayName("scopes refetch ledger by slot source spec and entity")
  void scopes_refetch_ledger_by_slot_source_spec_and_entity() {
    BoardRefetchResult result =
        new BoardRefetchGuard()
            .apply(currentRequest(), List.of(markerSignalWithSameEntityIdAsPath()));

    assertThat(result.ledger())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.applyStatus()).isEqualTo(BoardRefetchLedgerStatus.APPLIED);
              assertThat(entry.slot()).isEqualTo("marker");
              assertThat(entry.sourceSpec()).isEqualTo("S5");
              assertThat(entry.entityId()).isEqualTo("path-precinct-mixed-001");
              assertThat(entry.previousVersion()).isEqualTo(-1);
            });
    assertThat(result.board().slotRow("path", "path-precinct-mixed-001").version()).isEqualTo(2);
    assertThat(result.board().slotRow("marker", "path-precinct-mixed-001").version()).isEqualTo(1);
  }

  @Test
  @DisplayName("applies multiple slot rows from the same eventId when row scope differs")
  void applies_multiple_slot_rows_from_the_same_event_id_when_row_scope_differs() {
    BoardRefetchResult result =
        new BoardRefetchGuard()
            .apply(emptyOpRequest(), List.of(opToggleSignal(), opHistorySignal()));

    assertThat(result.ledger())
        .extracting(BoardRefetchLedgerEntry::applyStatus)
        .containsExactly(BoardRefetchLedgerStatus.APPLIED, BoardRefetchLedgerStatus.APPLIED);
    assertThat(result.board().slotRow("op_toggle", "op-precinct-001-op1").latestEventId())
        .isEqualTo("evt-s8-op-transition-001");
    assertThat(result.board().slotRow("op_history", "op-precinct-001-op2").latestEventId())
        .isEqualTo("evt-s8-op-transition-001");
  }

  @Test
  @DisplayName("converges delayed refetch trigger to the newer area version")
  void converges_delayed_refetch_trigger_to_the_newer_area_version() {
    BoardRefetchResult result =
        new BoardRefetchGuard().apply(staleAreaRequest(), List.of(delayedAreaStateSignal()));

    assertThat(result.ledger())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.eventId()).isEqualTo("evt-s2-area-state-001");
              assertThat(entry.applyStatus()).isEqualTo(BoardRefetchLedgerStatus.APPLIED);
              assertThat(entry.boardResponseVersion()).isEqualTo(3);
            });
    BoardSlotRow areaRow = result.board().slotRow("area", "area-precinct-a1");
    assertThat(areaRow.version()).isEqualTo(3);
    assertThat(areaRow.sequence()).isEqualTo(403);
  }

  @Test
  @DisplayName("records gone_refetch_required as a full board reload requirement")
  void records_gone_refetch_required_as_full_board_reload_requirement() {
    BoardRefetchResult result =
        new BoardRefetchGuard().goneRefetchRequired(currentRequest(), "evt-s4-gap-before-800");

    assertThat(result.ledger())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.eventId()).isEqualTo("evt-s4-gap-before-800");
              assertThat(entry.incidentId()).isEqualTo(INCIDENT_ID);
              assertThat(entry.applyStatus()).isEqualTo(BoardRefetchLedgerStatus.REFETCH_REQUIRED);
              assertThat(entry.reloadReason()).isEqualTo(BoardReloadReason.GONE_REFETCH_REQUIRED);
              assertThat(entry.boardResponseVersion()).isEqualTo(BOARD_RESPONSE_VERSION);
            });
    assertThat(result.board().slotRow("path", "path-precinct-mixed-001").version()).isEqualTo(2);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> toastRows(BoardDTO board) {
    return (List<Map<String, Object>>) board.slots().get("toast");
  }

  private static BoardAssemblyRequest currentRequest() {
    return new BoardAssemblyRequest(
        INCIDENT_ID,
        BOARD_RESPONSE_ID,
        BOARD_RESPONSE_VERSION,
        SERVER_TS,
        ACTIVE_OP_ID,
        SELECTED_OP_IDS,
        GEOMETRY_HASH,
        List.of(currentPathRow(), currentToastRow()));
  }

  private static BoardAssemblyRequest staleAreaRequest() {
    return new BoardAssemblyRequest(
        INCIDENT_ID,
        BOARD_RESPONSE_ID,
        2,
        SERVER_TS,
        ACTIVE_OP_ID,
        SELECTED_OP_IDS,
        GEOMETRY_HASH,
        List.of(areaRow(2, 402, "evt-s2-area-created-001", "hash-s2-area-a1-current")));
  }

  private static BoardAssemblyRequest emptyOpRequest() {
    return new BoardAssemblyRequest(
        INCIDENT_ID,
        BOARD_RESPONSE_ID,
        BOARD_RESPONSE_VERSION,
        SERVER_TS,
        ACTIVE_OP_ID,
        SELECTED_OP_IDS,
        GEOMETRY_HASH,
        List.of());
  }

  private static BoardRefetchSignal signal(BoardSourceRow row) {
    String eventType = row.slot().equals("path") ? "PATH_APPENDED" : "SUPPORT_REQUEST_CREATED";
    return BoardRefetchSignal.fromRow(INCIDENT_ID, eventType, SERVER_TS, row);
  }

  private static BoardSourceRow currentPathRow() {
    return pathRow(
        2,
        502,
        "evt-s3-path-appended-001",
        "hash-s3-path-mixed-current",
        Map.of("geometryHash", "hash-geometry-path-v2"));
  }

  private static BoardSourceRow stalePathEvent() {
    return pathRow(
        1,
        501,
        "evt-s3-path-appended-stale-001",
        "hash-s3-path-mixed-stale",
        Map.of("geometryHash", "hash-geometry-path-v1"));
  }

  private static BoardSourceRow missingPredecessorPathEvent() {
    return pathRow(
        3,
        505,
        "evt-s3-path-appended-gap-003",
        "hash-s3-path-mixed-gap",
        Map.of("geometryHash", "hash-geometry-path-v3-gap"));
  }

  private static BoardSourceRow newerPathEvent() {
    return pathRow(
        3,
        503,
        "evt-s3-path-appended-002",
        "hash-s3-path-mixed-v3",
        Map.of("geometryHash", "hash-geometry-path-v3"));
  }

  private static BoardSourceRow pathRow(
      long version, long sequence, String eventId, String sourceHash, Map<String, Object> extras) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("opId", ACTIVE_OP_ID);
    payload.put("policePhoneId", "dev-precinct-car-01");
    payload.put("geometryHash", extras.get("geometryHash"));
    payload.put("geometry", Map.of("type", "LineString"));
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
        payload);
  }

  private static BoardSourceRow areaRow(
      long version, long sequence, String eventId, String sourceHash) {
    return new BoardSourceRow(
        "area",
        "S2",
        "area-precinct-a1",
        "board-area-precinct-a1",
        "ASSIGNED",
        version,
        sequence,
        eventId,
        sourceHash,
        Map.of(
            "opId",
            ACTIVE_OP_ID,
            "geometryHash",
            "hash-geometry-area-a1",
            "geometry",
            Map.of("type", "Polygon")));
  }

  private static BoardRefetchSignal delayedAreaStateSignal() {
    return BoardRefetchSignal.fromRow(
        INCIDENT_ID,
        "AREA_STATE_CHANGED",
        SERVER_TS,
        areaRow(3, 403, "evt-s2-area-state-001", "hash-s2-area-a1-v3"));
  }

  private static BoardRefetchSignal markerSignalWithSameEntityIdAsPath() {
    return new BoardRefetchSignal(
        "evt-s5-marker-created-same-entity-001",
        INCIDENT_ID,
        "MARKER_CREATED",
        SERVER_TS,
        603,
        "marker",
        "S5",
        "path-precinct-mixed-001",
        "ACTIVE",
        1,
        603,
        "hash-s5-marker-same-entity",
        Map.of(
            "opId",
            ACTIVE_OP_ID,
            "markerType",
            "CLUE",
            "geometryHash",
            "hash-geometry-marker-same-entity",
            "geometry",
            Map.of("type", "Point")));
  }

  private static BoardRefetchSignal opToggleSignal() {
    return new BoardRefetchSignal(
        "evt-s8-op-transition-001",
        INCIDENT_ID,
        "OP_TRANSITIONED",
        SERVER_TS,
        801,
        "op_toggle",
        "S8",
        "op-precinct-001-op1",
        "ACTIVE",
        1,
        801,
        "hash-s8-op1-current",
        Map.of("opId", "op-precinct-001-op1"));
  }

  private static BoardRefetchSignal opHistorySignal() {
    return new BoardRefetchSignal(
        "evt-s8-op-transition-001",
        INCIDENT_ID,
        "OP_TRANSITIONED",
        SERVER_TS,
        802,
        "op_history",
        "S8",
        "op-precinct-001-op2",
        "OPENED",
        2,
        802,
        "hash-s8-op2-current",
        Map.of("opId", "op-precinct-001-op2"));
  }

  private static BoardSourceRow currentToastRow() {
    return toastRow("evt-s5-support-request-001");
  }

  private static BoardSourceRow duplicateToastEvent() {
    return toastRow("evt-s5-support-request-001");
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
        Map.of("markerId", "mk-precinct-support-001", "messageKey", "support_request_created"));
  }
}
