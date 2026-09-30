package com.surimap.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.surimap.api.service.sse.ServerSentEventHistoryService;
import com.surimap.global.event.EventPublishRequest;
import com.surimap.global.event.EventPublisher;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
class ServerSentEventJobWorkerTest extends PostGisIntegrationTestSupport {

  private UUID incidentId;
  private static final UUID EVENT_ID = UUID.fromString("40000000-0000-4000-8000-000000000953");

  @Autowired private EventPublisher eventHub;
  @Autowired private IncidentMapper incidentMapper;
  @Autowired private PlatformTransactionManager txManager;
  @Autowired private ServerSentEventHistoryService replayService;
  @Autowired private ServerSentEventConnectionRegistry connectionRegistry;
  @Autowired private ServerSentEventJobMapper jobMapper;
  @Autowired private ServerSentEventJobService jobService;
  @Autowired private ServerSentEventJobWorker worker;

  @BeforeEach
  void prepare_test_incident() {
    incidentId = UUID.randomUUID();
    Instant now = ServerSentEventTestSupport.CREATED_AT;
    incidentMapper.insertIncident(
        incidentId, UUID.randomUUID(), "SSE worker test", "OPEN", now, 1L, now);
  }

  @AfterEach
  void delete_test_incident() {
    connectionRegistry.closeIncidentConnections(incidentId);
    jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", incidentId);
    jdbcTemplate.update("DELETE FROM incident_data_purge WHERE incident_id = ?", incidentId);
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
      ServerSentEventMessage frame = sink.frames().get(0);
      assertThat(frame.getEvent()).isEqualTo("PATH_APPENDED");
      assertThat(frame.getId()).isEqualTo("1");
      assertThat(sink.sequenceAtSend).isEqualTo(1L);
      assertThat(sink.transactionActiveAtSend).isFalse();
      assertThat(sink.sendingThread).isNotEqualTo(requestThread);
      assertThat(frame.getData().getEventId()).isEqualTo(EVENT_ID);
      assertThat(frame.getData().getPayload().get("status")).isEqualTo("RECORDING");
      assertThat(replayService.getFirstPageAfter(incidentId, "0").getMessages())
          .extracting(frameValue -> frameValue.getData().getEventId())
          .contains(EVENT_ID);
      assertThat(getDispatchStatus()).isEqualTo("COMPLETED");
    } finally {
      registration.close();
    }
  }

  @Test
  @DisplayName("같은 트랜잭션의 마커 생성·발견 알림은 작업 UUID가 역순이어도 발행한 순서로 전송한다")
  void marker_created_and_person_found_are_sent_in_publication_order_when_job_ids_are_reversed()
      throws Exception {
    // given: 같은 마커의 생성 이벤트와 발견 알림을 받을 연결이 있다.
    EventPublishRequest personFound = createPersonFoundEvent();
    EventPublishRequest markerCreated =
        EventPublishRequest.builder()
            .eventId(UUID.randomUUID())
            .incidentId(incidentId)
            .type("MARKER_CREATED")
            .payloadFormatVersion(1)
            .sourceEntityType("marker")
            .sourceEntityId(personFound.getSourceEntityId())
            .occurredAt(personFound.getOccurredAt())
            .payload(personFound.getPayload())
            .build();
    var connected = new CapturingSink();
    try (var registration = connectionRegistry.registerForIncident(incidentId, connected)) {
      // when: 같은 저장 시각의 두 작업을 만들고 UUID 정렬이 발행 순서와 반대가 되게 한다.
      new TransactionTemplate(txManager)
          .executeWithoutResult(
              status -> {
                eventHub.publish(markerCreated);
                eventHub.publish(personFound);
                jdbcTemplate.update(
                    """
                    UPDATE event_dispatch_job SET id = CASE event_type
                        WHEN 'MARKER_CREATED' THEN '00000000-0000-0000-0000-000000009002'::uuid
                        ELSE '00000000-0000-0000-0000-000000009001'::uuid
                    END WHERE incident_id = ?
                    """,
                    incidentId);
                assertThat(
                        jdbcTemplate.queryForObject(
                            "SELECT count(DISTINCT created_at) FROM event_dispatch_job WHERE incident_id = ?",
                            Integer.class,
                            incidentId))
                    .isEqualTo(1);
                assertThat(connected.frames()).isEmpty();
              });
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () ->
                  assertThat(
                          jdbcTemplate.queryForObject(
                              "SELECT count(*) FROM event_dispatch_job WHERE incident_id = ? AND dispatch_status = 'COMPLETED'",
                              Integer.class,
                              incidentId))
                      .isEqualTo(2));

      // then: 생성 이벤트부터 순번을 확정해 전송하며 두 원본 이벤트 모두 유지한다.
      assertThat(connected.frames())
          .extracting(ServerSentEventMessage::getEvent)
          .containsExactly("MARKER_CREATED", "PERSON_FOUND");
      assertThat(connected.frames())
          .extracting(ServerSentEventMessage::getId)
          .containsExactly("1", "2");
      assertThat(connected.frames())
          .extracting(frame -> frame.getData().getEventId())
          .containsExactly(markerCreated.getEventId(), personFound.getEventId());
      assertThat(jobMapper.findBySseSequenceRange(incidentId, 0L, 2L, 2))
          .extracting(ServerSentEventJob::getEventType)
          .containsExactly("MARKER_CREATED", "PERSON_FOUND");
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
                SseEmitter.event().id(frame.getId()).name(frame.getEvent()).data(frame.getData()));
          } catch (IOException exception) {
            throw new UncheckedIOException(exception);
          }
        });
    var connected = new CapturingSink();
    try (var registration = connectionRegistry.registerForIncident(incidentId, connected)) {
      assertThat(connectionRegistry.getSenders(incidentId)).hasSize(2);
      // when: 실제 트랜잭션으로 발견 알림을 저장하고 커밋 후 전송한다.
      new TransactionTemplate(txManager)
          .executeWithoutResult(status -> eventHub.publish(createPersonFoundEvent()));

      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(getDispatchStatus()).isEqualTo("COMPLETED"));

      // then: 정상 연결은 수신하고, 이벤트는 재전송할 수 있도록 저장돼 있다.
      assertThat(connected.frames()).hasSize(1);
      assertThat(connected.frames().get(0).getEvent()).isEqualTo("PERSON_FOUND");
      assertThat(connected.frames().get(0).getData().getEventId()).isEqualTo(EVENT_ID);
      assertThat(connectionRegistry.getSenders(incidentId)).containsExactly(connected);
      assertThat(replayService.getFirstPageAfter(incidentId, "0").getMessages())
          .extracting(frameValue -> frameValue.getData().getEventId())
          .contains(EVENT_ID);
      // COMPLETED는 서버 전송 처리의 완료이며 브라우저 표시 확인이 아니다.
      assertThat(getDispatchStatus()).isEqualTo("COMPLETED");
    } finally {
      connectionRegistry.closeIncidentConnections(incidentId);
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
    LiveServerSentEventSender slowConnection =
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
    LiveServerSentEventSender failingConnection =
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
      assertThat(connected.frames()).extracting(ServerSentEventMessage::getId).containsExactly("1");
      assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(1L);
      assertThat(replayService.getFirstPageAfter(incidentId, "0").getMessages()).hasSize(1);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "DISPATCHING"})
  @DisplayName("시작 후 DB에 남은 대기·중단 작업도 깨우기 신호 없이 주기 조회로 전송한다")
  void periodic_scan_dispatches_unfinished_job_created_after_start_without_wake_signal(String state)
      throws Exception {
    // given: 기존 주기 설정으로 별도 worker를 시작한다.
    ServerSentEventJob job = ServerSentEventJob.from(createPathAppendedEvent());
    var pollingWorker =
        new ServerSentEventJobWorker(jobService, connectionRegistry, true, 0, 1000, 100);
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
        assertThat(connected.frames())
            .extracting(ServerSentEventMessage::getId)
            .containsExactly("1");
        assertThat(jobMapper.findById(job.getId()).getServerSentEventSequence()).isEqualTo(1L);
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
    ServerSentEventJob job = ServerSentEventJob.from(createPathAppendedEvent());
    jobMapper.insert(job);
    jobMapper.claimById(job.getId(), "DISPATCHING");
    if (hasSequence) {
      jobService.getOrAssignServerSentEventSequence(job.getId());
    }
    var restartedWorker =
        new ServerSentEventJobWorker(jobService, connectionRegistry, false, 0, 1000, 100);
    var connected = new CapturingSink();
    try (var registration = connectionRegistry.registerForIncident(incidentId, connected)) {
      try {
        // when: 새 worker를 시작한다. 재접속 조회나 실제 JVM 재시작 검증은 아니다.
        restartedWorker.start();
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(getDispatchStatus()).isEqualTo("COMPLETED"));

        // then: 중단된 작업을 자동으로 이어 보내며 원래 DB 순번을 유지한다.
        assertThat(connected.frames())
            .extracting(ServerSentEventMessage::getId)
            .containsExactly("1");
        assertThat(jobMapper.findById(job.getId()).getServerSentEventSequence()).isEqualTo(1L);
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
    LiveServerSentEventSender temporaryFailure =
        frame -> {
          if (attempts.incrementAndGet() == 1) {
            throw new IllegalStateException(
                "temporary send failure", new IllegalArgumentException("test failure"));
          }
          connected.send(frame);
        };
    var pollingWorker =
        new ServerSentEventJobWorker(jobService, connectionRegistry, true, 0, 1000, 100);
    try (var registration = connectionRegistry.registerForIncident(incidentId, temporaryFailure)) {
      try {
        // when: 한 worker가 최초 전송과 실패 후 재시도를 모두 담당한다. 수동 상태 변경은 없다.
        jobMapper.insert(ServerSentEventJob.from(createPersonFoundEvent()));
        pollingWorker.start();
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(getDispatchStatus()).isEqualTo("COMPLETED"));

        // then: 실패 작업이 자동으로 재전송되며 새 순번을 만들지 않는다.
        assertThat(attempts).hasValue(2);
        assertThat(connected.frames())
            .extracting(ServerSentEventMessage::getId)
            .containsExactly("1");
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
    ServerSentEventJob first = ServerSentEventJob.from(createPathAppendedEvent());
    ServerSentEventJob second =
        ServerSentEventJob.from(
            ServerSentEventTestSupport.publishRequest(
                UUID.randomUUID(),
                incidentId,
                "PATH_APPENDED",
                UUID.randomUUID().toString(),
                "RECORDING",
                4L));
    jobMapper.insert(first);
    jobMapper.insert(second);
    jobService.getOrAssignServerSentEventSequence(first.getId());
    jobService.getOrAssignServerSentEventSequence(second.getId());
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
      assertThat(connected.frames())
          .extracting(ServerSentEventMessage::getId)
          .containsExactly("1", "2");
    }
  }

  @Test
  @DisplayName("DB에 확정한 이벤트를 트랜잭션 밖에서 보내고 같은 순번과 내용으로 재조회한다")
  void dispatch_sends_committed_sequence_and_payload_outside_transaction() {
    // given: 이벤트의 내용과 순번을 DB에 확정했다.
    var job = save_event("PATH_APPENDED");
    var connection = new CapturingSseConnection();
    connectionRegistry.registerForIncident(incidentId, connection);

    // when: DB에서 읽은 이벤트를 전송한다.
    ServerSentEventTestSupport.dispatchLiveEvent(
        jobService, connectionRegistry, job.toPublishRequest(), job.getServerSentEventSequence());

    // then: 전송 시점에 이미 DB에 있고 재전송 조회도 같은 내용을 반환한다.
    assertThat(connection.frames).hasSize(1);
    assertThat(connection.storedSequencesAtSend).containsExactly(1L);
    assertThat(connection.transactionActiveAtSend).isFalse();
    var frame = connection.frames.get(0);
    assertThat(frame.getId()).isEqualTo("1");
    assertThat(frame.getEvent()).isEqualTo("PATH_APPENDED");
    assertThat(frame.getData()).isEqualTo(job.toPublishRequest());
    assertThat(replayService.getFirstPageAfter(incidentId, "0").getMessages())
        .containsExactly(frame);
  }

  @Test
  @DisplayName("같은 작업을 재시도하면 DB 이력은 하나로 유지하고 같은 순번으로 다시 보낸다")
  void dispatch_retry_preserves_single_history_and_original_sequence() {
    // given: DB에 하나의 전송 작업을 확정했다.
    var job = save_event("PATH_APPENDED");
    var connection = new CapturingSseConnection();
    connectionRegistry.registerForIncident(incidentId, connection);

    // when: 같은 작업의 전송을 두 번 시도한다.
    ServerSentEventTestSupport.dispatchLiveEvent(
        jobService, connectionRegistry, job.toPublishRequest(), job.getServerSentEventSequence());
    ServerSentEventTestSupport.dispatchLiveEvent(
        jobService,
        connectionRegistry,
        job.toPublishRequest(),
        jobService.getOrAssignServerSentEventSequence(job.getId()));

    // then: 같은 순번으로 두 번 보내지만 DB에는 하나만 남는다.
    assertThat(connection.frames)
        .extracting(ServerSentEventMessage::getId)
        .containsExactly("1", "1");
    assertThat(replayService.getFirstPageAfter(incidentId, "0").getMessages()).hasSize(1);
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
    ServerSentEventTestSupport.dispatchLiveEvent(
        jobService,
        connectionRegistry,
        closed.toPublishRequest(),
        closed.getServerSentEventSequence());

    // then: 종료만 알리고 실제 연결과 등록을 정리한다.
    assertThat(connection.frames)
        .extracting(ServerSentEventMessage::getEvent)
        .containsExactly("INCIDENT_CLOSED");
    assertThat(connection.frames)
        .extracting(ServerSentEventMessage::getId)
        .containsExactly(hasPrevious ? "2" : "1");
    assertThat(connection.closed).isTrue();
    assertThat(connectionRegistry.getSenders(incidentId)).isEmpty();
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
            () ->
                ServerSentEventTestSupport.dispatchLiveEvent(
                    jobService,
                    connectionRegistry,
                    path.toPublishRequest(),
                    path.getServerSentEventSequence()))
        .isInstanceOf(ServerSentEventRefetchRequiredException.class);

    // when / then: 새 연결·이어받기는 종료 알림만 받거나 파기 완료로 거부된다.
    for (String lastId : new String[] {null, "1"}) {
      if (purged) {
        assertThatThrownBy(() -> replayService.getFirstPageAfter(incidentId, lastId))
            .isInstanceOf(ServerSentEventRefetchRequiredException.class);
      } else {
        assertThat(replayService.getFirstPageAfter(incidentId, lastId).getMessages())
            .extracting(ServerSentEventMessage::getEvent)
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
              () ->
                  ServerSentEventTestSupport.dispatchLiveEvent(
                      jobService,
                      connectionRegistry,
                      path.toPublishRequest(),
                      path.getServerSentEventSequence()));
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
            .extracting(ServerSentEventMessage::getEvent)
            .containsExactly("PATH_APPENDED");
      } else {
        assertThatThrownBy(() -> delivery.get(10, java.util.concurrent.TimeUnit.SECONDS))
            .hasCauseInstanceOf(ServerSentEventRefetchRequiredException.class);
        assertThat(laterConnection.frames).isEmpty();
      }
    } finally {
      releaseFirstSend.complete(null);
      executor.shutdown();
      assertThat(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
  }

  private ServerSentEventJob save_event(String type) {
    EventPublishRequest request =
        ServerSentEventTestSupport.publishRequest(UUID.randomUUID(), incidentId, type);
    var job = ServerSentEventJob.from(request);
    jobMapper.insert(job);
    jobService.getOrAssignServerSentEventSequence(job.getId());
    return jobMapper.findById(job.getId());
  }

  private final class CapturingSseConnection implements LiveServerSentEventSender {
    private final List<ServerSentEventMessage> frames = new ArrayList<>();
    private final List<Long> storedSequencesAtSend = new ArrayList<>();
    private boolean transactionActiveAtSend;
    private boolean closed;

    @Override
    public void send(ServerSentEventMessage frame) {
      transactionActiveAtSend = TransactionSynchronizationManager.isActualTransactionActive();
      storedSequencesAtSend.add(
          jdbcTemplate.queryForObject(
              "SELECT sse_sequence FROM event_dispatch_job WHERE event_id = ?",
              Long.class,
              frame.getData().getEventId()));
      frames.add(frame);
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private EventPublishRequest createPersonFoundEvent() {
    var markerId = UUID.fromString("30000000-0000-4000-8000-000000000953");
    return EventPublishRequest.builder()
        .eventId(EVENT_ID)
        .incidentId(incidentId)
        .type("PERSON_FOUND")
        .payloadFormatVersion(1)
        .sourceEntityType("marker")
        .sourceEntityId(markerId)
        .occurredAt(ServerSentEventTestSupport.CREATED_AT)
        .payload(Map.of("id", markerId.toString(), "status", "ACTIVE", "version", 1))
        .build();
  }

  private EventPublishRequest createPathAppendedEvent() {
    return ServerSentEventTestSupport.publishRequest(
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

  private final class CapturingSink implements LiveServerSentEventSender {
    private final List<ServerSentEventMessage> frames = new CopyOnWriteArrayList<>();

    private Long sequenceAtSend;
    private boolean transactionActiveAtSend;
    private String sendingThread;

    @Override
    public void send(ServerSentEventMessage frame) {
      transactionActiveAtSend = TransactionSynchronizationManager.isActualTransactionActive();
      sendingThread = Thread.currentThread().getName();
      sequenceAtSend =
          jdbcTemplate.queryForObject(
              "SELECT sse_sequence FROM event_dispatch_job WHERE event_id = ?",
              Long.class,
              frame.getData().getEventId());
      frames.add(frame);
    }

    List<ServerSentEventMessage> frames() {
      return frames;
    }
  }
}
