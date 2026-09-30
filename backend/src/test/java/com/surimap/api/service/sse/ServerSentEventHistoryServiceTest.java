package com.surimap.api.service.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.global.sse.ServerSentEventFormatter;
import com.surimap.global.sse.ServerSentEventJob;
import com.surimap.global.sse.ServerSentEventJobMapper;
import com.surimap.global.sse.ServerSentEventJobService;
import com.surimap.global.sse.ServerSentEventMessage;
import com.surimap.global.sse.ServerSentEventRefetchRequiredException;
import com.surimap.global.sse.ServerSentEventTestSupport;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class ServerSentEventHistoryServiceTest extends PostGisIntegrationTestSupport {

  private UUID incidentId;
  @Autowired private ServerSentEventHistoryService replayService;
  @Autowired private ServerSentEventJobMapper jobMapper;
  @Autowired private ServerSentEventJobService jobService;
  @Autowired private IncidentMapper incidentMapper;

  @BeforeEach
  void prepare_test_incident() {
    incidentId = UUID.randomUUID();
    incidentMapper.insertIncident(
        incidentId,
        UUID.randomUUID(),
        "SSE DB replay test",
        "OPEN",
        ServerSentEventTestSupport.CREATED_AT,
        1L,
        ServerSentEventTestSupport.CREATED_AT);
  }

  @AfterEach
  void delete_test_incident() {
    jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", incidentId);
    jdbcTemplate.update("DELETE FROM incident_data_purge WHERE incident_id = ?", incidentId);
    jdbcTemplate.update("DELETE FROM incident WHERE id = ?", incidentId);
  }

  @Test
  @DisplayName("이 서버가 실시간으로 보내지 않았던 이벤트도 DB 순번부터 이어서 조회한다")
  void replay_reads_committed_db_history_without_previous_live_dispatch() {
    // given: 실시간 전송을 거치지 않고 DB에만 확정한 두 이벤트다.
    var first =
        ServerSentEventJob.from(
            ServerSentEventTestSupport.publishRequest(
                UUID.randomUUID(), incidentId, "PATH_APPENDED"));
    var second =
        ServerSentEventJob.from(
            ServerSentEventTestSupport.publishRequest(
                UUID.randomUUID(), incidentId, "PATH_APPENDED"));
    jobMapper.insert(first);
    jobMapper.insert(second);
    jobService.getOrAssignServerSentEventSequence(first.getId());
    jobService.getOrAssignServerSentEventSequence(second.getId());

    // when: 첫 이벤트까지 받은 상황판이 다시 연결한다.
    var frames = replayService.getFirstPageAfter(incidentId, "1").getMessages();

    // then: 서버 메모리에 없더라도 DB의 두 번째 이벤트를 원래 순번과 내용으로 반환한다.
    assertThat(frames).extracting(ServerSentEventMessage::getId).containsExactly("2");
    assertThat(frames.get(0).getData().getEventId()).isEqualTo(second.getEventId());
  }

  @Test
  @DisplayName("DB에 역순으로 저장돼 있어도 순번대로 읽고 SSE 본문을 그대로 유지한다")
  void replay_orders_events_by_sequence_and_preserves_envelope() {
    // given: 순번 902를 먼저, 901을 나중에 저장했다.
    save_event(902, "MARKER_CREATED");
    var path = save_event(901, "PATH_APPENDED");

    // when: 순번 900 이후의 이벤트를 요청한다.
    var frames = replayService.getFirstPageAfter(incidentId, "900").getMessages();

    // then: 저장 순서가 아니라 순번을 따르며 DB JSON을 기존 SSE 형식으로 보낸다.
    assertThat(frames).extracting(ServerSentEventMessage::getId).containsExactly("901", "902");
    assertThat(frames)
        .extracting(ServerSentEventMessage::getEvent)
        .containsExactly("PATH_APPENDED", "MARKER_CREATED");
    assertThat(ServerSentEventFormatter.format(frames.get(0)))
        .contains(
            "id:901",
            "event:PATH_APPENDED",
            "\"eventId\":\"" + path.getEventId() + "\"",
            "\"incidentId\":\"" + incidentId + "\"",
            "\"type\":\"PATH_APPENDED\"",
            "\"payloadFormatVersion\":1",
            "\"occurredAt\":\"2026-05-08T00:00:00Z\"",
            "\"id\":\"30000000-0000-4000-8000-000000000501\"",
            "\"status\":\"RECORDING\"",
            "\"version\":7");
  }

  @Test
  @DisplayName("이어받을 순번이 누락되면 재조회를 요구하고 새 연결에는 남은 이벤트를 반환한다")
  void replay_with_missing_sequence_requires_refetch_and_fresh_replay_returns_retained_events() {
    // given: 44와 46만 저장해 45가 누락돼 있다.
    save_event(44, "PATH_APPENDED");
    save_event(46, "PATH_APPENDED");

    // when / then: 44 이후 이어받기는 거부한다.
    assertThatThrownBy(() -> replayService.getFirstPageAfter(incidentId, "44"))
        .isInstanceOf(ServerSentEventRefetchRequiredException.class);

    // when / then: 마지막 수신 순번 없는 새 연결은 남은 이벤트를 순서대로 읽는다.
    assertThat(replayService.getFirstPageAfter(incidentId, null).getMessages())
        .extracting(ServerSentEventMessage::getId)
        .containsExactly("44", "46");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @DisplayName("100개씩 읽는 동안 새 이벤트가 생겨도 최초 확정 범위까지만 순서대로 반환한다")
  void replay_pages_preserve_initial_end_sequence_when_new_event_is_committed(boolean resumed) {
    // given: 한 페이지를 넘는 DB 이력을 첫 조회로 읽었다.
    for (long sequence = 1; sequence <= 205; sequence++) {
      save_event(sequence, "PATH_APPENDED");
    }
    var first = replayService.getFirstPageAfter(incidentId, resumed ? "1" : null);
    assertThat(first.getMessages()).hasSize(100);
    assertThat(first.getMessages().get(0).getId()).isEqualTo(resumed ? "2" : "1");

    // when: 다음 페이지를 읽기 전에 새 이벤트가 확정된다.
    save_event(206, "PATH_APPENDED");
    var second = replayService.getNextPage(incidentId, first);
    var third = replayService.getNextPage(incidentId, second);

    // then: 페이지 크기와 최초 마지막 순번을 유지하며 새 이벤트는 실시간 전달 대상으로 남긴다.
    assertThat(second.getMessages()).hasSize(100);
    assertThat(third.getMessages()).hasSize(resumed ? 4 : 5);
    assertThat(first.getThroughSequence()).isEqualTo(205);
    assertThat(second.getThroughSequence()).isEqualTo(205);
    assertThat(third.getThroughSequence()).isEqualTo(205);
    assertThat(third.hasMore()).isFalse();
    var frames =
        java.util.stream.Stream.of(first, second, third)
            .flatMap(page -> page.getMessages().stream())
            .toList();
    for (int index = 0; index < frames.size(); index++) {
      assertThat(frames.get(index).getId()).isEqualTo(Long.toString(index + (resumed ? 2 : 1)));
    }
    assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(206);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2", "150", "205", "ALL"})
  @DisplayName("이어받을 이력의 앞·뒤 페이지·끝·전체가 없으면 첫 조회부터 재조회를 요구한다")
  void missing_history_is_rejected_before_first_page_is_returned(String missing) {
    // given: 이력 205개 가운데 필요한 순번이 사라졌다.
    for (long sequence = 1; sequence <= 205; sequence++) {
      save_event(sequence, "PATH_APPENDED");
    }
    if (missing.equals("ALL")) {
      jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", incidentId);
    } else {
      jdbcTemplate.update(
          "DELETE FROM event_dispatch_job WHERE incident_id = ? AND sse_sequence = ?",
          incidentId,
          Long.parseLong(missing));
    }

    // when / then: 첫 페이지가 온전해도 전체 범위의 누락을 정상 재전송으로 숨기지 않는다.
    assertThatThrownBy(() -> replayService.getFirstPageAfter(incidentId, "1"))
        .isInstanceOf(ServerSentEventRefetchRequiredException.class);
    assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(205);
  }

  @ParameterizedTest
  @ValueSource(strings = {"MISSING", "CLOSED", "PURGED"})
  @DisplayName("다음 페이지 전에 이력이 삭제되거나 사건이 종료·파기되면 과거 내용을 계속 보내지 않는다")
  void next_page_rechecks_missing_history_and_terminal_state(String change) {
    // given: 첫 페이지를 읽고 전송할 수 있는 상태다.
    for (long sequence = 1; sequence <= 102; sequence++) {
      save_event(sequence, "PATH_APPENDED");
    }
    var first = replayService.getFirstPageAfter(incidentId, "1");
    if (change.equals("MISSING")) {
      jdbcTemplate.update(
          "DELETE FROM event_dispatch_job WHERE incident_id = ? AND sse_sequence = 102",
          incidentId);
    } else {
      save_event(103, "INCIDENT_CLOSED");
      jdbcTemplate.update("UPDATE incident SET status = 'CLOSED' WHERE id = ?", incidentId);
      if (change.equals("PURGED")) {
        jdbcTemplate.update(
            """
            INSERT INTO incident_data_purge
                (id, incident_id, status, closed_at, purge_due_at, completed_at,
                 environment_policy, created_at, updated_at)
            VALUES (?, ?, 'COMPLETED', now(), now(), now(), 'PRODUCTION_IMMEDIATE', now(), now())
            """,
            UUID.randomUUID(),
            incidentId);
      }
    }

    // when / then: 종료라면 종료 알림만 읽고, 누락·파기라면 재조회를 요구한다.
    if (change.equals("CLOSED")) {
      var terminal = replayService.getNextPage(incidentId, first);
      assertThat(terminal.getMessages())
          .extracting(ServerSentEventMessage::getEvent)
          .containsExactly("INCIDENT_CLOSED");
      assertThat(terminal.getMessages())
          .extracting(ServerSentEventMessage::getId)
          .containsExactly("103");
      assertThat(terminal.isTerminalReached()).isTrue();
      assertThat(terminal.hasMore()).isFalse();
    } else {
      assertThatThrownBy(() -> replayService.getNextPage(incidentId, first))
          .isInstanceOf(ServerSentEventRefetchRequiredException.class);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "invalid", "9223372036854775808", "2"})
  @DisplayName("잘못된 마지막 순번이나 DB보다 앞선 순번은 정상 빈 응답으로 처리하지 않는다")
  void invalid_or_future_cursor_requires_refetch(String lastId) {
    // given: 순번 1까지만 확정돼 있다.
    save_event(1, "PATH_APPENDED");
    // when / then: 잘못된 재접속 위치를 거부한다.
    assertThatThrownBy(() -> replayService.getFirstPageAfter(incidentId, lastId))
        .isInstanceOf(ServerSentEventRefetchRequiredException.class);
  }

  @Test
  @DisplayName("순번 없는 과거 완료 작업은 새 연결에도 재전송하지 않고 그대로 보존한다")
  void fresh_replay_does_not_assign_or_send_legacy_unsequenced_job() {
    // given: 과거 완료 작업은 순번 없이 남아 있다.
    var legacy =
        ServerSentEventJob.from(
            ServerSentEventTestSupport.publishRequest(
                UUID.randomUUID(), incidentId, "PATH_APPENDED"));
    jobMapper.insert(legacy);
    jdbcTemplate.update(
        "UPDATE event_dispatch_job SET dispatch_status = 'COMPLETED' WHERE id = ?", legacy.getId());
    save_event(1, "PATH_APPENDED");

    // when: 수신 순번 없는 새 연결이 이력을 읽는다.
    var frames = replayService.getFirstPageAfter(incidentId, null).getMessages();

    // then: 확정된 이벤트만 읽고 과거 작업·사건 순번을 바꾸지 않는다.
    assertThat(frames).extracting(ServerSentEventMessage::getId).containsExactly("1");
    assertThat(jobMapper.findById(legacy.getId()).getServerSentEventSequence()).isNull();
    assertThat(jobMapper.findById(legacy.getId()).getDispatchStatus()).isEqualTo("COMPLETED");
    assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(1);
  }

  private ServerSentEventJob save_event(long sequence, String type) {
    var job =
        ServerSentEventJob.from(
            ServerSentEventTestSupport.publishRequest(
                UUID.randomUUID(),
                incidentId,
                type,
                "30000000-0000-4000-8000-000000000501",
                "RECORDING",
                7L));
    jobMapper.insert(job);
    jobMapper.assignSseSequenceIfAbsent(job.getId(), sequence);
    jdbcTemplate.update(
        "UPDATE incident SET last_sse_sequence = GREATEST(last_sse_sequence, ?) WHERE id = ?",
        sequence,
        incidentId);
    return job;
  }
}
