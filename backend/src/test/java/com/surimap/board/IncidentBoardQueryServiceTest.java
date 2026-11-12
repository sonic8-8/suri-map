package com.surimap.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.surimap.global.sse.ServerSentEventConnectionRegistry;
import com.surimap.global.sse.ServerSentEventJobService;
import com.surimap.global.sse.ServerSentEventJobWorker;
import com.surimap.global.sse.ServerSentEventTestSupport;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.operationalperiod.InitialOperationalPeriodCreationService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IncidentBoardQueryServiceTest extends PostGisIntegrationTestSupport {

  private UUID incidentId;
  @Autowired private IncidentBoardQueryService boardQueryService;
  @Autowired private IncidentMapper incidentMapper;
  @Autowired private InitialOperationalPeriodCreationService opCreationService;
  @Autowired private ServerSentEventJobService jobService;
  @Autowired private ServerSentEventConnectionRegistry connectionRegistry;

  @BeforeEach
  void prepare_test_incident() {
    incidentId = UUID.randomUUID();
    incidentMapper.insertIncident(
        incidentId,
        UUID.randomUUID(),
        "board SSE sequence test",
        "OPEN",
        ServerSentEventTestSupport.CREATED_AT,
        1L,
        ServerSentEventTestSupport.CREATED_AT);
  }

  @AfterEach
  void delete_test_incident() {
    jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", incidentId);
    jdbcTemplate.update("DELETE FROM operational_period WHERE incident_id = ?", incidentId);
    jdbcTemplate.update("DELETE FROM incident WHERE id = ?", incidentId);
  }

  @Test
  @DisplayName("이벤트 순번이 확정된 변경은 상황판 응답에 들어 있다. 응답의 SSE 순번은 그 순번 이상이다")
  void board_contains_change_of_sequenced_event_and_last_sse_sequence_covers_it() {
    // given: 수색 차수를 만든 변경이 커밋됐다. 그 변경의 이벤트에 순번이 확정됐다.
    UUID opId = opCreationService.createOp1(incidentId).opId();
    long eventSequence = dispatchPendingEventAndGetSequence();

    // when: 상황판을 조회한다.
    BoardDTO board = boardQueryService.getBoard(incidentId, null, List.of("op_toggle"), null);

    // then: 그 변경이 응답에 들어 있다. 응답의 SSE 순번은 그 이벤트의 순번 이상이다.
    assertThat(board.slotRow("op_toggle", opId.toString()).status()).isEqualTo("ACTIVE");
    assertThat(board.lastSseSequence()).isGreaterThanOrEqualTo(eventSequence);
  }

  @Test
  @DisplayName("이벤트가 없는 사건의 상황판 응답의 SSE 순번은 0이다")
  void board_of_incident_without_events_has_zero_last_sse_sequence() {
    // when: 이벤트가 하나도 없는 사건의 상황판을 조회한다.
    BoardDTO board = boardQueryService.getBoard(incidentId, null, null, null);

    // then: 응답의 SSE 순번은 0이다.
    assertThat(board.lastSseSequence()).isZero();
  }

  /** 테스트용 worker를 공개 진입점으로 깨운다. 대기 중인 이벤트에 순번이 확정되고 전송이 끝날 때까지 기다린다. */
  private long dispatchPendingEventAndGetSequence() {
    var worker = new ServerSentEventJobWorker(jobService, connectionRegistry, true, 0, 100, 100);
    try {
      worker.start();
      worker.wake();
      await()
          .atMost(Duration.ofSeconds(10))
          .until(
              () ->
                  jdbcTemplate.queryForObject(
                          "SELECT count(*) FROM event_dispatch_job"
                              + " WHERE incident_id = ? AND dispatch_status = 'COMPLETED'",
                          Integer.class,
                          incidentId)
                      == 1);
    } finally {
      worker.stop();
    }
    return jdbcTemplate.queryForObject(
        "SELECT sse_sequence FROM event_dispatch_job WHERE incident_id = ?",
        Long.class,
        incidentId);
  }
}
