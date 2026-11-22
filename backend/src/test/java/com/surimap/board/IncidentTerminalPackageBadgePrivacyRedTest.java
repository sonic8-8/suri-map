package com.surimap.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L6-T09B RED: SC-12 terminal board state must stay sanitized after purge. */
@DisplayName("L6-T09B incident_terminal privacy board state RED")
class IncidentTerminalPackageBadgePrivacyRedTest {

  private static final UUID INCIDENT_ID = UUID.fromString("77777777-0000-4000-8000-0000000009b1");
  private static final OffsetDateTime SERVER_TS = OffsetDateTime.parse("2026-05-07T09:11:00+09:00");
  private static final List<String> TERMINAL_RESPONSE_KEYS =
      List.of(
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

  @Test
  @DisplayName("closed/purged board response exposes incident_terminal tombstone fields only")
  void closedPurgedBoardResponseExposesIncidentTerminalTombstoneFieldsOnly() {
    BoardDTO board = boardWithRows(List.of(terminalRowWithForbiddenSourceFields()));

    @SuppressWarnings("unchecked")
    Map<String, Object> terminal = (Map<String, Object>) board.slots().get("incident_terminal");

    assertThat(terminal.keySet()).containsExactlyInAnyOrderElementsOf(TERMINAL_RESPONSE_KEYS);
    assertThat(terminal)
        .containsEntry("incidentId", INCIDENT_ID.toString())
        .containsEntry("terminalStatus", "PURGED")
        .containsEntry("closedStatus", "purged")
        .containsEntry("closedAt", "2026-05-07T09:10:00+09:00")
        .containsEntry("writeDisabledReason", "purged")
        .containsEntry("localPurgeState", "completed");
    assertThat(board.slotSources().get("incident_terminal"))
        .singleElement()
        .satisfies(
            cursor -> {
              assertThat(cursor.id()).isEqualTo(INCIDENT_ID.toString());
              assertThat(cursor.status()).isEqualTo("PURGED");
              assertThat(cursor.version()).isEqualTo(12L);
              assertThat(cursor.sequence()).isEqualTo(912L);
              assertThat(cursor.sourceSpec()).isEqualTo("S1-3");
            });
    assertThat(String.valueOf(board.slots()))
        .doesNotContain(
            "missing_person",
            "missingPerson",
            "displayName",
            "photoObjectKey",
            "lastSeenLocationText",
            "latestLocation",
            "packageReloadUrl",
            "streamResubscribeHint",
            "resubscribe",
            "reload");
  }

  private BoardDTO boardWithRows(List<BoardSourceRow> rows) {
    return new BoardAssembler()
        .assemble(
            new BoardAssemblyRequest(
                INCIDENT_ID.toString(),
                "board-response-l6-t09b-terminal-package",
                12L,
                SERVER_TS,
                null,
                List.of(),
                "hash-board-terminal-purged",
                rows));
  }

  private static BoardSourceRow terminalRowWithForbiddenSourceFields() {
    return new BoardSourceRow(
        "incident_terminal",
        "S1-3",
        INCIDENT_ID.toString(),
        "board-incident-terminal-l6-t09b",
        "PURGED",
        12L,
        912L,
        "evt-s1-3-incident-purged-l6-t09b",
        "hash-s1-3-terminal-purged",
        Map.ofEntries(
            Map.entry("incidentId", INCIDENT_ID.toString()),
            Map.entry("terminalStatus", "PURGED"),
            Map.entry("closedStatus", "purged"),
            Map.entry("closedAt", "2026-05-07T09:10:00+09:00"),
            Map.entry("writeDisabledReason", "purged"),
            Map.entry("localPurgeState", "completed"),
            Map.entry("missing_person", Map.of("displayName", "가상 실종자")),
            Map.entry(
                "missingPerson", Map.of("photoObjectKey", "photo/missing-person/purge-target.jpg")),
            Map.entry("lastSeenLocationText", "무등산 서측 탐방로"),
            Map.entry(
                "latestLocation",
                Map.of("type", "Point", "coordinates", List.of(126.9134, 35.1631))),
            Map.entry("packageReloadUrl", "/api/incidents/" + INCIDENT_ID + "/packages/reload"),
            Map.entry("streamResubscribeHint", "/api/incidents/" + INCIDENT_ID + "/events")));
  }
}
