package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.eventhub.adapter.EventDispatchJob;
import com.surimap.eventhub.adapter.EventDispatchJobMapper;
import com.surimap.eventhub.adapter.EventDispatchJobService;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.stream.GoneRefetchRequiredException;
import com.surimap.eventhub.stream.SseConnectionRegistry;
import com.surimap.eventhub.stream.SseEventFrame;
import com.surimap.eventhub.stream.SseLiveEventSink;
import com.surimap.eventhub.stream.SseReplayService;
import com.surimap.eventhub.stream.SseStreamService;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SseStreamServiceTest extends PostGisIntegrationTestSupport {

  private UUID incidentId;
  @Autowired private SseStreamService streamService;
  @Autowired private SseReplayService replayService;
  @Autowired private EventDispatchJobMapper jobMapper;
  @Autowired private EventDispatchJobService jobService;
  @Autowired private IncidentMapper incidentMapper;
  @Autowired private SseConnectionRegistry connectionRegistry;

  @BeforeEach
  void prepare_test_incident() {
    incidentId = UUID.randomUUID();
    incidentMapper.insertIncident(
        incidentId,
        UUID.randomUUID(),
        "SSE live test",
        "OPEN",
        EventStreamTestFixtures.CREATED_AT,
        1L,
        EventStreamTestFixtures.CREATED_AT);
  }

  @AfterEach
  void close_connections_and_delete_test_incident() {
    connectionRegistry.closeIncidentConnections(incidentId);
    jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", incidentId);
    jdbcTemplate.update("DELETE FROM incident_data_purge WHERE incident_id = ?", incidentId);
    jdbcTemplate.update("DELETE FROM incident WHERE id = ?", incidentId);
  }

  @Test
  @DisplayName("DB에 확정한 이벤트를 트랜잭션 밖에서 보내고 같은 순번과 내용으로 재조회한다")
  void dispatch_sends_committed_sequence_and_payload_outside_transaction() {
    // given: 이벤트의 내용과 순번을 DB에 확정했다.
    var job = save_event("PATH_APPENDED");
    var connection = new CapturingSseConnection();
    connectionRegistry.registerForIncident(incidentId, connection);

    // when: DB에서 읽은 이벤트를 전송한다.
    streamService.dispatchLive(job.toPublishRequest(), job.getSseSequence());

    // then: 전송 시점에 이미 DB에 있고 재전송 조회도 같은 내용을 반환한다.
    assertThat(connection.frames).hasSize(1);
    assertThat(connection.storedSequencesAtSend).containsExactly(1L);
    assertThat(connection.transactionActiveAtSend).isFalse();
    var frame = connection.frames.get(0);
    assertThat(frame.id()).isEqualTo("1");
    assertThat(frame.event()).isEqualTo("PATH_APPENDED");
    assertThat(frame.data()).isEqualTo(job.toPublishRequest());
    assertThat(replayService.replayResultAfter(incidentId, "0").getFrames()).containsExactly(frame);
  }

  @Test
  @DisplayName("같은 작업을 재시도하면 DB 이력은 하나로 유지하고 같은 순번으로 다시 보낸다")
  void dispatch_retry_preserves_single_history_and_original_sequence() {
    // given: DB에 하나의 전송 작업을 확정했다.
    var job = save_event("PATH_APPENDED");
    var connection = new CapturingSseConnection();
    connectionRegistry.registerForIncident(incidentId, connection);

    // when: 같은 작업의 전송을 두 번 시도한다.
    streamService.dispatchLive(job.toPublishRequest(), job.getSseSequence());
    streamService.dispatchLive(
        job.toPublishRequest(), jobService.getOrAssignSseSequence(job.getId()));

    // then: 같은 순번으로 두 번 보내지만 DB에는 하나만 남는다.
    assertThat(connection.frames).extracting(SseEventFrame::id).containsExactly("1", "1");
    assertThat(replayService.replayResultAfter(incidentId, "0").getFrames()).hasSize(1);
    assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(1L);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @DisplayName("이전 이력 유무와 관계없이 종료를 다음 순번으로 알리고 연결을 닫는다")
  void dispatch_closed_incident_sends_terminal_event_and_removes_connection(boolean hasPrevious) {
    // given: 종료 상태와 종료 이벤트가 DB에 저장돼 있다.
    if (hasPrevious) {
      save_event("PATH_APPENDED");
    }
    var closed = save_event("INCIDENT_CLOSED");
    jdbcTemplate.update("UPDATE incident SET status = 'CLOSED' WHERE id = ?", incidentId);
    var connection = new CapturingSseConnection();
    connectionRegistry.registerForIncident(incidentId, connection);

    // when: 종료 알림을 전송한다.
    streamService.dispatchLive(closed.toPublishRequest(), closed.getSseSequence());

    // then: 종료만 알리고 실제 연결과 등록을 정리한다.
    assertThat(connection.frames)
        .extracting(SseEventFrame::event)
        .containsExactly("INCIDENT_CLOSED");
    assertThat(connection.frames)
        .extracting(SseEventFrame::id)
        .containsExactly(hasPrevious ? "2" : "1");
    assertThat(connection.closed).isTrue();
    assertThat(connectionRegistry.sinks(incidentId)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @DisplayName("DB에 종료·파기 상태가 기록되면 과거 내용을 새 연결이나 실시간 전송으로 보내지 않는다")
  void terminal_db_state_blocks_old_payload_on_reconnect_and_live_dispatch(boolean purged) {
    // given: 경로 이력이 남은 사건을 종료했고 파기 완료 여부도 DB에 기록했다.
    var path = save_event("PATH_APPENDED");
    var closed = save_event("INCIDENT_CLOSED");
    jdbcTemplate.update("UPDATE incident SET status = 'CLOSED' WHERE id = ?", incidentId);
    if (purged) {
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

    // when / then: 이미 선점해 둔 일반 이벤트도 DB 상태를 확인해 보내지 않는다.
    assertThatThrownBy(
            () -> streamService.dispatchLive(path.toPublishRequest(), path.getSseSequence()))
        .isInstanceOf(GoneRefetchRequiredException.class);

    // when / then: 새 연결·이어받기는 종료 알림만 받거나 파기 완료로 거부된다.
    for (String lastId : new String[] {null, "1"}) {
      if (purged) {
        assertThatThrownBy(() -> replayService.replayResultAfter(incidentId, lastId))
            .isInstanceOf(GoneRefetchRequiredException.class);
      } else {
        assertThat(replayService.replayResultAfter(incidentId, lastId).getFrames())
            .extracting(SseEventFrame::event)
            .containsExactly("INCIDENT_CLOSED");
      }
    }
    // 재전송 차단과 DB 원본 삭제는 다르다. 원본 정리 정책은 후속 작업이다.
    assertThat(jobMapper.findById(path.getId())).isNotNull();
    assertThat(jobMapper.findById(closed.getId())).isNotNull();
  }

  @ParameterizedTest(name = "전송 대기 중 사건 상태: {0}")
  @ValueSource(strings = {"OPEN", "CLOSED", "PURGED"})
  @DisplayName("상태 조회 뒤 전송 대기 중 사건이 종료·파기되면 뒤 연결에 과거 내용을 보내지 않는다")
  void terminal_state_committed_during_dispatch_blocks_payload_to_later_connection(String state)
      throws Exception {
    // given: DB 상태 검사를 마친 뒤 첫 연결의 전송에서 대기한다.
    var path = save_event("PATH_APPENDED");
    var firstSendStarted = new java.util.concurrent.CountDownLatch(1);
    var releaseFirstSend = new java.util.concurrent.CompletableFuture<Void>();
    connectionRegistry.registerForIncident(
        incidentId,
        ignored -> {
          firstSendStarted.countDown();
          releaseFirstSend.join();
        });
    var laterConnection = new CapturingSseConnection();
    connectionRegistry.registerForIncident(incidentId, laterConnection);
    var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    try {
      var delivery =
          executor.submit(
              () -> streamService.dispatchLive(path.toPublishRequest(), path.getSseSequence()));
      assertThat(firstSendStarted.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

      // when: 별도 DB 연결에서 종료·파기를 커밋한 뒤 첫 연결의 대기를 푼다.
      if (!state.equals("OPEN")) {
        save_event("INCIDENT_CLOSED");
        jdbcTemplate.update("UPDATE incident SET status = 'CLOSED' WHERE id = ?", incidentId);
      }
      if (state.equals("PURGED")) {
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
      releaseFirstSend.complete(null);

      // then: 진행 중이면 전달하고, 종료·파기 뒤 아직 쓰지 않은 과거 내용은 전달하지 않는다.
      if (state.equals("OPEN")) {
        delivery.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(laterConnection.frames)
            .extracting(SseEventFrame::event)
            .containsExactly("PATH_APPENDED");
      } else {
        assertThatThrownBy(() -> delivery.get(10, java.util.concurrent.TimeUnit.SECONDS))
            .hasCauseInstanceOf(GoneRefetchRequiredException.class);
        assertThat(laterConnection.frames).isEmpty();
      }
    } finally {
      releaseFirstSend.complete(null);
      executor.shutdown();
      assertThat(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
  }

  private EventDispatchJob save_event(String type) {
    PublishRequest request =
        EventStreamTestFixtures.publishRequest(UUID.randomUUID(), incidentId, type);
    var job = EventDispatchJob.from(request);
    jobMapper.insert(job);
    jobService.getOrAssignSseSequence(job.getId());
    return jobMapper.findById(job.getId());
  }

  private final class CapturingSseConnection implements SseLiveEventSink {
    private final List<SseEventFrame> frames = new ArrayList<>();
    private final List<Long> storedSequencesAtSend = new ArrayList<>();
    private boolean transactionActiveAtSend;
    private boolean closed;

    @Override
    public void send(SseEventFrame frame) {
      transactionActiveAtSend = TransactionSynchronizationManager.isActualTransactionActive();
      storedSequencesAtSend.add(
          jdbcTemplate.queryForObject(
              "SELECT sse_sequence FROM event_dispatch_job WHERE event_id = ?",
              Long.class,
              frame.data().eventId()));
      frames.add(frame);
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
