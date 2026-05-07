package com.surimap.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.board.fixture.BoardAssemblyLagObservationFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L6-T02C RED: SSE refetch convergence and board API assembly lag observation. */
@DisplayName("L6-T02C SSE refetch convergence and assembly lag")
class BoardRefetchConvergenceLagRedTest {

  @Test
  @DisplayName("exposes a convergence probe after an SSE event refetch applies")
  void exposes_convergence_probe_after_sse_event_refetch_applies() {
    BoardRefetchResult result =
        new BoardRefetchGuard()
            .apply(
                BoardAssemblyLagObservationFixtures.staleBoardRequest(),
                List.of(BoardAssemblyLagObservationFixtures.delayedAreaStateSignal()));

    BoardRefetchConvergenceProbe probe = result.convergenceProbe();

    assertThat(probe.eventId()).isEqualTo(BoardAssemblyLagObservationFixtures.EVENT_ID);
    assertThat(probe.slot()).isEqualTo(BoardAssemblyLagObservationFixtures.SLOT);
    assertThat(probe.sourceSpec()).isEqualTo(BoardAssemblyLagObservationFixtures.SOURCE_SPEC);
    assertThat(probe.entityId()).isEqualTo(BoardAssemblyLagObservationFixtures.ENTITY_ID);
    assertThat(probe.sourceResponseVersion())
        .isEqualTo(BoardAssemblyLagObservationFixtures.SOURCE_RESPONSE_VERSION);
    assertThat(probe.sourceResponseSequence())
        .isEqualTo(BoardAssemblyLagObservationFixtures.SOURCE_RESPONSE_SEQUENCE);
    assertThat(probe.boardRowVersion())
        .isEqualTo(BoardAssemblyLagObservationFixtures.SOURCE_RESPONSE_VERSION);
    assertThat(probe.boardRowSequence())
        .isEqualTo(BoardAssemblyLagObservationFixtures.SOURCE_RESPONSE_SEQUENCE);
    assertThat(probe.converged()).isTrue();
  }

  @Test
  @DisplayName("exposes STALE_REFETCH when GET board still lags behind the SSE source version")
  void exposes_stale_refetch_when_board_api_response_lags_source_version() {
    BoardAssemblyLagState lagState =
        new BoardRefetchGuard()
            .observeAssemblyLag(
                BoardAssemblyLagObservationFixtures.staleBoardRequest(),
                BoardAssemblyLagObservationFixtures.delayedAreaStateSignal());

    assertThat(lagState.uiState()).isEqualTo(BoardAssemblyLagObservationFixtures.EXPECTED_UI_STATE);
    assertThat(lagState.eventId()).isEqualTo(BoardAssemblyLagObservationFixtures.EVENT_ID);
    assertThat(lagState.slot()).isEqualTo(BoardAssemblyLagObservationFixtures.SLOT);
    assertThat(lagState.entityId()).isEqualTo(BoardAssemblyLagObservationFixtures.ENTITY_ID);
    assertThat(lagState.sourceResponseVersion())
        .isEqualTo(BoardAssemblyLagObservationFixtures.SOURCE_RESPONSE_VERSION);
    assertThat(lagState.staleResponseVersion())
        .isEqualTo(BoardAssemblyLagObservationFixtures.STALE_RESPONSE_VERSION);
    assertThat(lagState.reloadAssertion())
        .isEqualTo(BoardAssemblyLagObservationFixtures.EXPECTED_RELOAD_ASSERTION);
  }

  @Test
  @DisplayName("lag observation fixture keeps the documented delayed_refetch_trigger values exact")
  void lag_observation_fixture_keeps_documented_delayed_refetch_trigger_values_exact() {
    BoardAssemblyRequest staleBoard = BoardAssemblyLagObservationFixtures.staleBoardRequest();
    BoardRefetchSignal signal = BoardAssemblyLagObservationFixtures.delayedAreaStateSignal();

    assertThat(signal.eventId()).isEqualTo("evt-s2-area-state-001");
    assertThat(signal.eventType()).isEqualTo("SEARCH_AREA_CHANGED");
    assertThat(signal.slot()).isEqualTo("area");
    assertThat(signal.entityId()).isEqualTo("area-precinct-a1");
    assertThat(signal.version()).isEqualTo(3);
    assertThat(signal.sequence()).isEqualTo(403);
    assertThat(staleBoard.boardResponseVersion()).isEqualTo(2);
    assertThat(staleBoard.sourceRows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.slot()).isEqualTo("area");
              assertThat(row.sourceResponseId()).isEqualTo("area-precinct-a1");
              assertThat(row.version()).isEqualTo(2);
              assertThat(row.sequence()).isEqualTo(402);
            });
  }

  @Test
  @DisplayName("convergence probe tolerates valid board responses that intentionally hide a row")
  void convergence_probe_tolerates_valid_board_responses_that_intentionally_hide_a_row() {
    BoardRefetchResult result =
        new BoardRefetchGuard()
            .apply(
                BoardAssemblyLagObservationFixtures.terminalBoardRequest(),
                List.of(BoardAssemblyLagObservationFixtures.policePhoneFreshnessSignal()));

    BoardRefetchConvergenceProbe probe = result.convergenceProbe();

    assertThat(probe.eventId()).isEqualTo("evt-s1-2-heartbeat-closed-001");
    assertThat(probe.slot()).isEqualTo("police_phone_freshness");
    assertThat(probe.boardRowVersion()).isEqualTo(-1);
    assertThat(probe.boardRowSequence()).isEqualTo(-1);
    assertThat(probe.converged()).isFalse();
  }
}
