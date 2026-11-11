package com.surimap.api.service.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.surimap.global.event.EventPublisher;
import com.surimap.global.sse.ServerSentEventConnectionRegistry;
import com.surimap.global.sse.ServerSentEventFormatter;
import com.surimap.global.sse.ServerSentEventJob;
import com.surimap.global.sse.ServerSentEventJobMapper;
import com.surimap.global.sse.ServerSentEventJobService;
import com.surimap.global.sse.ServerSentEventJobWorker;
import com.surimap.global.sse.ServerSentEventMessage;
import com.surimap.global.sse.ServerSentEventRefetchRequiredException;
import com.surimap.global.sse.ServerSentEventStream;
import com.surimap.global.sse.ServerSentEventTestSupport;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.retention.purge.PurgeRunMapper;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ServerSentEventHistoryServiceTest extends PostGisIntegrationTestSupport {

  private UUID incidentId;
  @Autowired private ServerSentEventHistoryService replayService;
  @Autowired private ServerSentEventJobMapper jobMapper;
  @Autowired private ServerSentEventJobService jobService;
  @Autowired private IncidentMapper incidentMapper;
  @Autowired private PurgeRunMapper purgeRunMapper;
  @Autowired private ServerSentEventSubscriptionService subscriptionService;
  @Autowired private ServerSentEventConnectionRegistry connectionRegistry;
  @Autowired private EventPublisher eventPublisher;
  @Autowired private PlatformTransactionManager txManager;

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
    connectionRegistry.closeIncidentConnections(incidentId);
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
  @DisplayName("순번 없이 연결하면 과거 이벤트 없이 연결한 뒤 생긴 이벤트부터 받는다")
  void fresh_connection_skips_past_events_and_receives_events_after_connect() throws Exception {
    // given: 진행 중인 사건에 순번 1, 2가 확정돼 있다.
    save_event(1, "PATH_APPENDED");
    save_event(2, "MARKER_CREATED");

    // when: 마지막 수신 순번 없이 연결한다. 그 뒤 순번 3이 확정돼 전송된다.
    var frames = openIncidentStream(subscriptionService, null);
    publishAndDispatch("PATH_APPENDED");

    // then: 연결한 뒤 생긴 순번 3만 받는다.
    assertThat(sentIds(frames)).containsExactly("3");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "2")
  @DisplayName("연결 전에 순번이 확정된 작업이 연결 뒤 다시 처리돼도 그 과거 이벤트는 받지 않는다")
  void jobs_sequenced_before_connect_are_not_sent_when_retried_after_connect(String lastEventId)
      throws Exception {
    // given: 연결 전에 순번 1의 지원 요청은 전송 중에 중단됐다. 순번 2의 발견 이벤트는 전송에 실패했다.
    var interrupted = save_event(1, "SUPPORT_REQUEST_CREATED");
    var failed = save_event(2, "PERSON_FOUND");
    set_dispatch_status(interrupted, "DISPATCHING");
    set_dispatch_status(failed, "FAILED");

    // when: 순번 없이(또는 순번 2로) 연결한다. 그 뒤 두 작업이 다시 처리된다. 순번 3도 새로 발행된다.
    var frames = openIncidentStream(subscriptionService, lastEventId);
    publishAndDispatch("PATH_APPENDED");

    // then: 두 과거 작업은 다시 처리됐다. 연결에는 연결한 뒤 생긴 순번 3만 전송됐다.
    assertThat(dispatch_status(interrupted)).isEqualTo("COMPLETED");
    assertThat(dispatch_status(failed)).isEqualTo("COMPLETED");
    assertThat(sentIds(frames)).containsExactly("3");
  }

  @Test
  @DisplayName("연결을 등록한 뒤 끝 순번을 읽기 전에 생긴 이벤트는 한 번만 받는다")
  void event_committed_between_registration_and_history_end_read_is_sent_once() throws Exception {
    // given: 순번 1, 2가 확정돼 있다. 연결이 등록된 뒤 끝 순번을 읽기 직전에 순번 3이 확정돼 전송된다.
    save_event(1, "PATH_APPENDED");
    save_event(2, "PATH_APPENDED");
    var eventSentDuringStart = new AtomicBoolean();
    // 연결이 등록된 뒤 첫 끝 순번 읽기 직전에 이벤트가 발행되도록 공개 메서드를 덮어쓴다.
    var jobsWithEventDuringStart =
        new ServerSentEventJobService(jobMapper, incidentMapper, purgeRunMapper) {
          @Override
          public long getHistoryEndSequence(UUID targetIncidentId) {
            if (!connectionRegistry.getSenders(targetIncidentId).isEmpty()
                && eventSentDuringStart.compareAndSet(false, true)) {
              publishAndDispatch("PATH_APPENDED");
            }
            return super.getHistoryEndSequence(targetIncidentId);
          }
        };
    var subscriptions =
        new ServerSentEventSubscriptionService(
            new ServerSentEventHistoryService(jobsWithEventDuringStart),
            jobService,
            connectionRegistry);

    // when: 마지막 수신 순번 없이 연결한다.
    var frames = openIncidentStream(subscriptions, null);

    // then: 이력과 대기열에 모두 잡힌 순번 3을 한 번만 받는다. 그 앞의 이력은 받지 않는다.
    assertThat(eventSentDuringStart).isTrue();
    assertThat(sentIds(frames)).containsExactly("3");
  }

  @Test
  @DisplayName("순번 0으로 연결하면 1번부터 빈틈없이 받는다")
  void zero_cursor_connection_receives_history_from_first_sequence() throws Exception {
    // given: 순번 1, 2가 확정돼 있다.
    save_event(1, "PATH_APPENDED");
    save_event(2, "PATH_APPENDED");

    // when: 0번까지 반영했다는 순번으로 연결한다.
    var frames = openIncidentStream(subscriptionService, "0");

    // then: 순번 없는 연결과 달리 1번부터 받는다.
    assertThat(sentIds(frames)).containsExactly("1", "2");
  }

  @Test
  @DisplayName("순번 0으로 연결할 때 이어 받을 이력에 빈틈이 있으면 연결이 거절된다")
  void zero_cursor_connection_with_missing_sequence_requires_refetch() {
    // given: 1과 3만 저장해 2가 누락돼 있다.
    save_event(1, "PATH_APPENDED");
    save_event(3, "PATH_APPENDED");

    // when / then: 남은 1과 3만 전송되는 대신 연결이 거절된다.
    assertThatThrownBy(() -> subscriptionService.openIncidentStream(incidentId, "0"))
        .isInstanceOf(ServerSentEventRefetchRequiredException.class);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @DisplayName("종료된 사건에 순번 없이 연결하면 종료 알림만 받는다. 파기가 완료된 사건이면 연결이 거절된다")
  void fresh_connection_to_closed_incident_receives_close_event_unless_purged(boolean purged)
      throws Exception {
    // given: 경로 이벤트 뒤에 종료 알림이 확정된 종료 사건이다.
    save_event(1, "PATH_APPENDED");
    save_event(2, "INCIDENT_CLOSED");
    jdbcTemplate.update("UPDATE incident SET status = 'CLOSED' WHERE id = ?", incidentId);
    if (purged) {
      complete_purge();
    }

    // when / then: 파기 전이면 종료 알림만 받는다. 파기 뒤면 연결이 거절된다.
    if (purged) {
      assertThatThrownBy(() -> subscriptionService.openIncidentStream(incidentId, null))
          .isInstanceOf(ServerSentEventRefetchRequiredException.class);
    } else {
      var frames = openIncidentStream(subscriptionService, null);
      assertThat(sentIds(frames)).containsExactly("2");
      assertThat(frames).anyMatch(frame -> frame.contains("event:INCIDENT_CLOSED"));
    }
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
  @DisplayName("이어받을 순번이 누락되면 이어받기가 거절된다. 순번 없는 새 연결에는 과거 이벤트가 전송되지 않는다")
  void replay_with_missing_sequence_requires_refetch_and_fresh_connection_sends_no_past_events()
      throws Exception {
    // given: 44와 46만 저장해 45가 누락돼 있다.
    save_event(44, "PATH_APPENDED");
    save_event(46, "PATH_APPENDED");

    // when / then: 44 이후 이어받기는 거부한다.
    assertThatThrownBy(() -> replayService.getFirstPageAfter(incidentId, "44"))
        .isInstanceOf(ServerSentEventRefetchRequiredException.class);

    // when / then: 마지막 수신 순번 없는 새 연결은 열린다. 남은 이벤트는 전송되지 않는다.
    assertThat(sentIds(openIncidentStream(subscriptionService, null))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1})
  @DisplayName("100개씩 읽는 동안 새 이벤트가 생겨도 최초 확정 범위까지만 순서대로 반환한다")
  void replay_pages_preserve_initial_end_sequence_when_new_event_is_committed(long lastSequence) {
    // given: 한 페이지를 넘는 DB 이력을 첫 조회로 읽었다.
    for (long sequence = 1; sequence <= 205; sequence++) {
      save_event(sequence, "PATH_APPENDED");
    }
    var first = replayService.getFirstPageAfter(incidentId, Long.toString(lastSequence));
    assertThat(first.getMessages()).hasSize(100);
    assertThat(first.getMessages().get(0).getId()).isEqualTo(Long.toString(lastSequence + 1));

    // when: 다음 페이지를 읽기 전에 새 이벤트가 확정된다.
    save_event(206, "PATH_APPENDED");
    var second = replayService.getNextPage(incidentId, first);
    var third = replayService.getNextPage(incidentId, second);

    // then: 페이지 크기와 최초 마지막 순번을 유지하며 새 이벤트는 실시간 전달 대상으로 남긴다.
    assertThat(second.getMessages()).hasSize(100);
    assertThat(third.getMessages()).hasSize((int) (5 - lastSequence));
    assertThat(first.getThroughSequence()).isEqualTo(205);
    assertThat(second.getThroughSequence()).isEqualTo(205);
    assertThat(third.getThroughSequence()).isEqualTo(205);
    assertThat(third.hasMore()).isFalse();
    var frames =
        java.util.stream.Stream.of(first, second, third)
            .flatMap(page -> page.getMessages().stream())
            .toList();
    for (int index = 0; index < frames.size(); index++) {
      assertThat(frames.get(index).getId()).isEqualTo(Long.toString(index + lastSequence + 1));
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
        complete_purge();
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
  @DisplayName("순번 없는 과거 완료 작업은 순번 없는 연결과 순번 0 연결에 전송되지 않는다. 작업은 그대로 보존된다")
  void legacy_unsequenced_job_is_not_sent_or_assigned_sequence() throws Exception {
    // given: 과거 완료 작업은 순번 없이 남아 있다.
    var legacy =
        ServerSentEventJob.from(
            ServerSentEventTestSupport.publishRequest(
                UUID.randomUUID(), incidentId, "PATH_APPENDED"));
    jobMapper.insert(legacy);
    jdbcTemplate.update(
        "UPDATE event_dispatch_job SET dispatch_status = 'COMPLETED' WHERE id = ?", legacy.getId());
    save_event(1, "PATH_APPENDED");

    // when: 순번 없이 연결한다. 순번 0으로도 연결한다.
    var fresh = openIncidentStream(subscriptionService, null);
    var fromZero = openIncidentStream(subscriptionService, "0");

    // then: 순번 없이 연결하면 과거 이벤트를 받지 않는다. 순번 0으로 연결하면 확정된 이벤트만 받는다.
    assertThat(sentIds(fresh)).isEmpty();
    assertThat(sentIds(fromZero)).containsExactly("1");
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
    // 전송이 끝난 과거 이벤트로 둔다. 그래서 테스트용 worker를 깨워도 다시 전송되지 않는다.
    jdbcTemplate.update(
        "UPDATE event_dispatch_job SET dispatch_status = 'COMPLETED' WHERE id = ?", job.getId());
    jdbcTemplate.update(
        "UPDATE incident SET last_sse_sequence = GREATEST(last_sse_sequence, ?) WHERE id = ?",
        sequence,
        incidentId);
    return job;
  }

  private void complete_purge() {
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

  private void set_dispatch_status(ServerSentEventJob job, String status) {
    jdbcTemplate.update(
        "UPDATE event_dispatch_job SET dispatch_status = ? WHERE id = ?", status, job.getId());
  }

  private String dispatch_status(ServerSentEventJob job) {
    return jdbcTemplate.queryForObject(
        "SELECT dispatch_status FROM event_dispatch_job WHERE id = ?", String.class, job.getId());
  }

  /**
   * 실제 발행 경로로 전송 작업을 커밋한다. 테스트용 worker를 공개 진입점으로 깨운 뒤 작업이 끝날 때까지 기다린다. 주기 조회를 켜 두어 실패한 작업도
   * 다시 처리된다.
   */
  private void publishAndDispatch(String type) {
    var request = ServerSentEventTestSupport.publishRequest(UUID.randomUUID(), incidentId, type);
    new TransactionTemplate(txManager)
        .executeWithoutResult(status -> eventPublisher.publish(request));
    var worker = new ServerSentEventJobWorker(jobService, connectionRegistry, true, 0, 100, 100);
    try {
      worker.start();
      worker.wake();
      await()
          .atMost(Duration.ofSeconds(10))
          .until(
              () ->
                  "COMPLETED"
                      .equals(
                          jdbcTemplate.queryForObject(
                              "SELECT dispatch_status FROM event_dispatch_job WHERE event_id = ?",
                              String.class,
                              request.getEventId())));
    } finally {
      worker.stop();
    }
  }

  /** 실제 구독 흐름으로 연결을 연다. Servlet 응답만 대역으로 둔다. 전송 요청은 그 자리에서 실행된다. */
  private List<String> openIncidentStream(
      ServerSentEventSubscriptionService subscriptions, String lastEventId) throws Exception {
    // worker 스레드에서도 프레임이 기록된다.
    List<String> frames = new CopyOnWriteArrayList<>();
    AsyncContext context = mock(AsyncContext.class);
    ServletResponse response = mock(ServletResponse.class);
    ServletOutputStream output = mock(ServletOutputStream.class);
    when(context.getResponse()).thenReturn(response);
    when(response.getOutputStream()).thenReturn(output);
    when(output.isReady()).thenReturn(true);
    doAnswer(
            call -> {
              ((Runnable) call.getArgument(0)).run();
              return null;
            })
        .when(context)
        .start(any(Runnable.class));
    doAnswer(
            call -> {
              frames.add(new String((byte[]) call.getArgument(0), StandardCharsets.UTF_8));
              return null;
            })
        .when(output)
        .write(any(byte[].class));
    var stream = (ServerSentEventStream) subscriptions.openIncidentStream(incidentId, lastEventId);
    stream.start(context, mock(ScheduledExecutorService.class));
    return frames;
  }

  private List<String> sentIds(List<String> frames) {
    return frames.stream()
        .flatMap(frame -> Arrays.stream(frame.split("\n")))
        .filter(line -> line.startsWith("id:"))
        .map(line -> line.substring("id:".length()))
        .toList();
  }
}
