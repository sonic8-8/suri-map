package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.surimap.eventhub.adapter.EventDispatchJob;
import com.surimap.eventhub.adapter.EventDispatchJobMapper;
import com.surimap.eventhub.adapter.EventDispatchJobService;
import com.surimap.eventhub.adapter.EventDispatchJobWorker;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.eventhub.stream.SseConnectionRegistry;
import com.surimap.eventhub.stream.SseEventFrame;
import com.surimap.eventhub.stream.SseLiveEventSink;
import com.surimap.eventhub.stream.SseReplayEventStore;
import com.surimap.eventhub.stream.SseStreamService;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@TestPropertySource(
    properties = {
      "surimap.eventhub.dispatch.enabled=true",
      "surimap.eventhub.dispatch.polling-enabled=false"
    })
class EventDispatchJobWorkerTest extends PostGisIntegrationTestSupport {

  private UUID incidentId;
  private static final UUID EVENT_ID = UUID.fromString("40000000-0000-4000-8000-000000000953");

  @Autowired private EventHub eventHub;
  @Autowired private IncidentMapper incidentMapper;
  @Autowired private PlatformTransactionManager txManager;
  @Autowired private SseReplayEventStore replayStore;
  @Autowired private SseConnectionRegistry connectionRegistry;
  @Autowired private SseStreamService streamService;
  @Autowired private EventDispatchJobMapper jobMapper;
  @Autowired private EventDispatchJobService jobService;
  @Autowired private EventDispatchJobWorker worker;

  @BeforeEach
  void prepare_test_incident() {
    replayStore.clear();
    incidentId = UUID.randomUUID();
    Instant now = EventStreamTestFixtures.CREATED_AT;
    incidentMapper.insertIncident(
        incidentId, UUID.randomUUID(), "SSE worker test", "OPEN", now, 1L, now);
  }

  @AfterEach
  void delete_test_incident() {
    connectionRegistry.closeIncidentConnections(incidentId);
    jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", incidentId);
    jdbcTemplate.update("DELETE FROM incident WHERE id = ?", incidentId);
  }

  @Test
  @DisplayName("업무 커밋 뒤 worker가 순번을 DB에 확정하고 트랜잭션 밖에서 전송한다")
  void committed_event_is_sent_and_dispatch_job_is_completed() throws Exception {
    // given: 사건 이벤트를 받을 전송 대상과 요청 처리 스레드가 있다.
    String requestThread = Thread.currentThread().getName();
    CapturingSink sink = new CapturingSink();
    AutoCloseable registration = connectionRegistry.registerForIncident(incidentId, sink);

    try {
      // when: 실제 트랜잭션으로 경로 추가 이벤트를 저장하고 커밋한다.
      new TransactionTemplate(txManager)
          .executeWithoutResult(
              status -> {
                eventHub.publish(createPathAppendedEvent());
                assertThat(sink.frames()).isEmpty();
              });
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(getDispatchStatus()).isEqualTo("COMPLETED"));

      // then: 전송 대상 호출·재전송 저장·전송 작업 완료를 확인한다.
      assertThat(sink.frames()).hasSize(1);
      SseEventFrame frame = sink.frames().get(0);
      assertThat(frame.event()).isEqualTo("PATH_APPENDED");
      assertThat(frame.id()).isEqualTo("1");
      assertThat(sink.sequenceAtSend).isEqualTo(1L);
      assertThat(sink.transactionActiveAtSend).isFalse();
      assertThat(sink.sendingThread).isNotEqualTo(requestThread);
      assertThat(frame.data().eventId()).isEqualTo(EVENT_ID);
      assertThat(frame.data().payload().get("status")).isEqualTo("RECORDING");
      assertThat(replayStore.findByEventId(EVENT_ID)).isPresent();
      assertThat(getDispatchStatus()).isEqualTo("COMPLETED");
    } finally {
      registration.close();
    }
  }

  @Test
  @DisplayName("닫힌 SSE 연결이 남아 있어도, 발견 알림을 정상 연결에 전송하고 작업을 완료한다")
  void dispatch_when_closed_connection_remains_sends_person_found_and_completes_job()
      throws Exception {
    // given: 종료 콜백이 처리되기 전에 새 이벤트가 들어오는 상황이다.
    var disconnected = new SseEmitter(0L);
    disconnected.completeWithError(new IllegalStateException("response already unusable"));
    connectionRegistry.registerForIncident(
        incidentId,
        frame -> {
          try {
            disconnected.send(
                SseEmitter.event().id(frame.id()).name(frame.event()).data(frame.data()));
          } catch (IOException exception) {
            throw new UncheckedIOException(exception);
          }
        });
    var connected = new CapturingSink();
    try (var registration = connectionRegistry.registerForIncident(incidentId, connected)) {
      assertThat(connectionRegistry.sinks(incidentId)).hasSize(2);
      // when: 실제 트랜잭션으로 발견 알림을 저장하고 커밋 후 전송한다.
      new TransactionTemplate(txManager)
          .executeWithoutResult(status -> eventHub.publish(createPersonFoundEvent()));

      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(getDispatchStatus()).isEqualTo("COMPLETED"));

      // then: 정상 연결은 수신하고, 이벤트는 재전송할 수 있도록 저장돼 있다.
      assertThat(connected.frames()).hasSize(1);
      assertThat(connected.frames().get(0).event()).isEqualTo("PERSON_FOUND");
      assertThat(connected.frames().get(0).data().eventId()).isEqualTo(EVENT_ID);
      assertThat(connectionRegistry.sinks(incidentId)).containsExactly(connected);
      assertThat(replayStore.findByEventId(EVENT_ID)).isPresent();
      // COMPLETED는 서버 전송 처리의 완료이며 브라우저 표시 확인이 아니다.
      assertThat(getDispatchStatus()).isEqualTo("COMPLETED");
    } finally {
      connectionRegistry.closeIncidentConnections(incidentId);
    }
  }

  @Test
  @DisplayName("재전송 저장에 실패하면, 알림을 전송하거나 작업을 완료 처리하지 않는다")
  void dispatch_when_replay_storage_rejects_event_marks_job_failed() throws Exception {
    // given: 파기된 사건은 재전송 저장소에 새 이벤트를 저장할 수 없다.
    replayStore.purgeIncident(incidentId);
    var connected = new CapturingSink();
    try (var registration = connectionRegistry.registerForIncident(incidentId, connected)) {
      // when: 저장이 거부될 발견 알림의 전송 작업을 처리한다.
      new TransactionTemplate(txManager)
          .executeWithoutResult(status -> eventHub.publish(createPersonFoundEvent()));

      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(getDispatchStatus()).isEqualTo("FAILED"));

      // then: 연결 하나의 실패와 달리 저장 실패는 전송 작업의 실패로 남는다.
      assertThat(connected.frames()).isEmpty();
      assertThat(replayStore.findByEventId(EVENT_ID)).isEmpty();
      assertThat(getDispatchStatus()).isEqualTo("FAILED");
    }
  }

  @Test
  @DisplayName("업무 저장이 롤백되면 전송 작업과 SSE 순번을 남기거나 이벤트를 보내지 않는다")
  void rolled_back_business_transaction_leaves_no_job_sequence_or_delivery() throws Exception {
    // given: 사건의 이벤트를 받을 연결이 있다.
    var connected = new CapturingSink();
    try (var registration = connectionRegistry.registerForIncident(incidentId, connected)) {
      // when: 이벤트를 저장한 업무 트랜잭션을 롤백한 뒤 worker를 실행해도 된다.
      new TransactionTemplate(txManager)
          .executeWithoutResult(
              status -> {
                eventHub.publish(createPathAppendedEvent());
                status.setRollbackOnly();
              });
      worker.wake();

      // then: 전송할 DB 행 자체가 없고 사건 순번도 소비하지 않는다.
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM event_dispatch_job WHERE incident_id = ?",
                  Integer.class,
                  incidentId))
          .isZero();
      assertThat(incidentMapper.findLastSseSequence(incidentId)).isZero();
      assertThat(connected.frames()).isEmpty();
    }
  }

  @Test
  @DisplayName("SSE 전송이 기다리는 중에도 마커 요청 쪽 커밋 처리는 반환된다")
  void slow_sse_send_does_not_hold_request_completion() throws Exception {
    // given: 전송을 일부러 붙잡아 두는 외부 연결이다.
    var sendStarted = new CountDownLatch(1);
    var allowSend = new CountDownLatch(1);
    SseLiveEventSink slowConnection =
        frame -> {
          sendStarted.countDown();
          try {
            if (!allowSend.await(10, TimeUnit.SECONDS)) {
              throw new IllegalStateException("test did not release SSE send");
            }
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
          }
        };
    try (var registration = connectionRegistry.registerForIncident(incidentId, slowConnection)) {
      try {
        // when: SSE 전송을 기다리지 않고 업무 커밋 호출이 반환된다.
        new TransactionTemplate(txManager)
            .executeWithoutResult(status -> eventHub.publish(createPersonFoundEvent()));

        // then: 요청은 반환됐지만 전송은 아직 완료되지 않았고 순번은 DB에 저장됐다.
        assertThat(sendStarted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(getDispatchStatus()).isEqualTo("DISPATCHING");
        assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(1L);
      } finally {
        allowSend.countDown();
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(getDispatchStatus()).isEqualTo("COMPLETED"));
      }
    }
  }

  @Test
  @DisplayName("전송 실패한 작업을 다시 실행하면 이력이 있어도 같은 순번으로 재전송한다")
  void retried_job_is_sent_again_with_original_committed_sequence() throws Exception {
    // given: 이력 저장 이후 전송에서 실패하는 연결이다.
    SseLiveEventSink failingConnection =
        frame -> {
          throw new IllegalStateException(
              "send failed", new IllegalArgumentException("test failure"));
        };
    try (var registration = connectionRegistry.registerForIncident(incidentId, failingConnection)) {
      new TransactionTemplate(txManager)
          .executeWithoutResult(status -> eventHub.publish(createPersonFoundEvent()));
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(getDispatchStatus()).isEqualTo("FAILED"));
    }
    var connected = new CapturingSink();
    try (var registration = connectionRegistry.registerForIncident(incidentId, connected)) {
      // when: 테스트에서 실패 작업을 대기로 돌린 뒤 다시 실행한다. 자동 복구 검증은 아니다.
      jdbcTemplate.update(
          "UPDATE event_dispatch_job SET dispatch_status = 'PENDING' WHERE event_id = ?", EVENT_ID);
      worker.wake();
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(getDispatchStatus()).isEqualTo("COMPLETED"));

      // then: 이력이 있다는 이유로 생략하지 않으며 새 순번을 소비하지 않는다.
      assertThat(connected.frames()).extracting(SseEventFrame::id).containsExactly("1");
      assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(1L);
      assertThat(replayStore.findByIncidentId(incidentId)).hasSize(1);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "DISPATCHING"})
  @DisplayName("시작 후 DB에 남은 대기·중단 작업도 깨우기 신호 없이 주기 조회로 전송한다")
  void periodic_scan_dispatches_unfinished_job_created_after_start_without_wake_signal(String state)
      throws Exception {
    // given: 기존 주기 설정으로 별도 worker를 시작한다.
    EventDispatchJob job = EventDispatchJob.from(createPathAppendedEvent());
    var pollingWorker = new EventDispatchJobWorker(jobService, streamService, true, 0, 1000, 100);
    var connected = new CapturingSink();
    try (var registration = connectionRegistry.registerForIncident(incidentId, connected)) {
      try {
        pollingWorker.start();
        // when: 시작 시 회수 이후에 미완료 작업을 커밋하고 깨우기 신호는 보내지 않는다.
        new TransactionTemplate(txManager)
            .executeWithoutResult(
                status -> {
                  jobMapper.insert(job);
                  if ("DISPATCHING".equals(state)) {
                    jobMapper.claimById(job.getId(), state);
                  }
                });
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(getDispatchStatus()).isEqualTo("COMPLETED"));

        // then: 깨우기 없이도 저장된 순번으로 전달한다. 프로세스 재시작 검증은 아니다.
        assertThat(connected.frames()).extracting(SseEventFrame::id).containsExactly("1");
        assertThat(jobMapper.findById(job.getId()).getSseSequence()).isEqualTo(1L);
      } finally {
        pollingWorker.stop();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @DisplayName("시작 전에 중단된 작업은 순번이 없으면 확정하고 있으면 그대로 이어서 전송한다")
  void startup_recovers_interrupted_job_without_reassigning_sequence(boolean hasSequence)
      throws Exception {
    // given: 이전 worker가 작업을 선점했지만 전송 결과를 남기지 못한 상태다.
    EventDispatchJob job = EventDispatchJob.from(createPathAppendedEvent());
    jobMapper.insert(job);
    jobMapper.claimById(job.getId(), "DISPATCHING");
    if (hasSequence) {
      jobService.getOrAssignSseSequence(job.getId());
    }
    var restartedWorker =
        new EventDispatchJobWorker(jobService, streamService, false, 0, 1000, 100);
    var connected = new CapturingSink();
    try (var registration = connectionRegistry.registerForIncident(incidentId, connected)) {
      try {
        // when: 새 worker를 시작한다. 재접속 조회나 실제 JVM 재시작 검증은 아니다.
        restartedWorker.start();
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(getDispatchStatus()).isEqualTo("COMPLETED"));

        // then: 중단된 작업을 자동으로 이어 보내며 원래 DB 순번을 유지한다.
        assertThat(connected.frames()).extracting(SseEventFrame::id).containsExactly("1");
        assertThat(jobMapper.findById(job.getId()).getSseSequence()).isEqualTo(1L);
        assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(1L);
        assertThat(jobMapper.findById(job.getId()).toPublishRequest())
            .isEqualTo(job.toPublishRequest());
      } finally {
        restartedWorker.stop();
      }
    }
  }

  @Test
  @DisplayName("일시적으로 실패한 전송 작업은 주기 조회에서 자동으로 다시 보내고 같은 순번을 유지한다")
  void periodic_scan_retries_failed_job_with_original_sequence() throws Exception {
    // given: 첫 전송만 실패하고 다음 전송은 받을 수 있는 연결이다.
    var attempts = new AtomicInteger();
    var connected = new CapturingSink();
    SseLiveEventSink temporaryFailure =
        frame -> {
          if (attempts.incrementAndGet() == 1) {
            throw new IllegalStateException(
                "temporary send failure", new IllegalArgumentException("test failure"));
          }
          connected.send(frame);
        };
    var pollingWorker = new EventDispatchJobWorker(jobService, streamService, true, 0, 1000, 100);
    try (var registration = connectionRegistry.registerForIncident(incidentId, temporaryFailure)) {
      try {
        // when: 한 worker가 최초 전송과 실패 후 재시도를 모두 담당한다. 수동 상태 변경은 없다.
        jobMapper.insert(EventDispatchJob.from(createPersonFoundEvent()));
        pollingWorker.start();
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(getDispatchStatus()).isEqualTo("COMPLETED"));

        // then: 실패 작업이 자동으로 재전송되며 새 순번을 만들지 않는다.
        assertThat(attempts).hasValue(2);
        assertThat(connected.frames()).extracting(SseEventFrame::id).containsExactly("1");
        assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(1L);
      } finally {
        pollingWorker.stop();
      }
    }
  }

  @Test
  @DisplayName("한 번 깨운 worker는 같은 사건의 앞 작업을 완료한 뒤 다음 작업도 순서대로 보낸다")
  void one_wake_dispatches_pending_jobs_of_same_incident_in_sequence() throws Exception {
    // given: 같은 사건에 순번을 확정한 두 작업이 저장돼 있고 주기 조회는 꺼져 있다.
    EventDispatchJob first = EventDispatchJob.from(createPathAppendedEvent());
    EventDispatchJob second =
        EventDispatchJob.from(
            EventStreamTestFixtures.publishRequest(
                UUID.randomUUID(),
                incidentId,
                "PATH_APPENDED",
                UUID.randomUUID().toString(),
                "RECORDING",
                4L));
    jobMapper.insert(first);
    jobMapper.insert(second);
    jobService.getOrAssignSseSequence(first.getId());
    jobService.getOrAssignSseSequence(second.getId());
    var connected = new CapturingSink();
    try (var registration = connectionRegistry.registerForIncident(incidentId, connected)) {
      // when: 깨우기 신호를 한 번만 보낸다.
      worker.wake();
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () ->
                  assertThat(jobMapper.findById(second.getId()).getDispatchStatus())
                      .isEqualTo("COMPLETED"));

      // then: 첫 작업이 끝난 후 다음 작업을 선점해서 같은 순서로 전송한다.
      assertThat(jobMapper.findById(first.getId()).getDispatchStatus()).isEqualTo("COMPLETED");
      assertThat(connected.frames()).extracting(SseEventFrame::id).containsExactly("1", "2");
    }
  }

  private PublishRequest createPersonFoundEvent() {
    var markerId = UUID.fromString("30000000-0000-4000-8000-000000000953");
    return new PublishRequest(
        EVENT_ID,
        incidentId,
        "PERSON_FOUND",
        1,
        "marker",
        markerId,
        EventStreamTestFixtures.CREATED_AT,
        Map.of("id", markerId.toString(), "status", "ACTIVE", "version", 1));
  }

  private PublishRequest createPathAppendedEvent() {
    return EventStreamTestFixtures.publishRequest(
        EVENT_ID,
        incidentId,
        "PATH_APPENDED",
        "30000000-0000-4000-8000-000000000953",
        "RECORDING",
        3L);
  }

  private String getDispatchStatus() {
    return jdbcTemplate.queryForObject(
        "SELECT dispatch_status FROM event_dispatch_job WHERE event_id = ?",
        String.class,
        EVENT_ID);
  }

  private final class CapturingSink implements SseLiveEventSink {
    private final List<SseEventFrame> frames = new CopyOnWriteArrayList<>();

    private Long sequenceAtSend;
    private boolean transactionActiveAtSend;
    private String sendingThread;

    @Override
    public void send(SseEventFrame frame) {
      transactionActiveAtSend = TransactionSynchronizationManager.isActualTransactionActive();
      sendingThread = Thread.currentThread().getName();
      sequenceAtSend =
          jdbcTemplate.queryForObject(
              "SELECT sse_sequence FROM event_dispatch_job WHERE event_id = ?",
              Long.class,
              frame.data().eventId());
      frames.add(frame);
    }

    List<SseEventFrame> frames() {
      return frames;
    }
  }
}
