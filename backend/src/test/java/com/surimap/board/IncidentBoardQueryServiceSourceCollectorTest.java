package com.surimap.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("incident board query service source collector")
class IncidentBoardQueryServiceSourceCollectorTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID OP_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
  private static final Instant SERVER_TS = Instant.parse("2026-04-28T01:30:00Z");

  @Test
  @DisplayName("assembles board response from collected source rows")
  void assembles_board_response_from_collected_source_rows() {
    CapturingCollector collector = new CapturingCollector();
    IncidentBoardQueryService service =
        new IncidentBoardQueryService(Clock.fixed(SERVER_TS, ZoneOffset.UTC), collector);

    BoardDTO board =
        service.getBoard(INCIDENT_ID, List.of(OP_ID), List.of("marker"), 12L);

    assertThat(collector.context().incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(collector.context().requestedOpIds()).containsExactly(OP_ID);
    assertThat(collector.context().includeSlots()).containsExactly("marker");
    assertThat(collector.context().sinceVersion()).isEqualTo(12L);
    assertThat(board.incidentId()).isEqualTo(INCIDENT_ID.toString());
    assertThat(board.activeOpId()).isEqualTo(OP_ID.toString());
    assertThat(board.selectedOpIds()).containsExactly(OP_ID.toString());
    assertThat(board.geometryHash()).isEqualTo("hash-board-test");
    assertThat(board.boardResponseVersion()).isEqualTo(33L);
    assertThat(board.slotRow("marker", "marker-source-001").payload())
        .containsEntry("markerType", "CLUE");
  }

  private static final class CapturingCollector implements IncidentBoardSourceRowCollector {
    private BoardSourceRowContext context;

    @Override
    public IncidentBoardSourceRowSnapshot collect(BoardSourceRowContext context) {
      this.context = context;
      return new IncidentBoardSourceRowSnapshot(
          OP_ID,
          List.of(OP_ID),
          "hash-board-test",
          List.of(
              new BoardSourceRow(
                  "marker",
                  "S5",
                  "marker-source-001",
                  "board-marker-source-001",
                  "ACTIVE",
                  33L,
                  601L,
                  "evt-s5-marker-source-001-v33",
                  "hash-s5-marker-source-001-v33",
                  Map.of("markerType", "CLUE"))));
    }

    private BoardSourceRowContext context() {
      return context;
    }
  }
}
