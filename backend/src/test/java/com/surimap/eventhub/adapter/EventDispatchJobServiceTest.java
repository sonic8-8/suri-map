package com.surimap.eventhub.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.stream.GoneRefetchRequiredException;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class EventDispatchJobServiceTest extends PostGisIntegrationTestSupport {

  @Autowired private EventDispatchJobService service;
  @Autowired private EventDispatchJobMapper mapper;
  @Autowired private IncidentMapper incidentMapper;
  @Autowired private PlatformTransactionManager transactionManager;

  private UUID incidentId;

  @BeforeEach
  void insert_incident() {
    incidentId = UUID.randomUUID();
    Instant now = Instant.parse("2026-09-26T00:00:00Z");
    incidentMapper.insertIncident(
        incidentId, UUID.randomUUID(), "SSE worker test", "OPEN", now, 7L, now);
  }

  @AfterEach
  void delete_test_incident() {
    jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", incidentId);
    jdbcTemplate.update("DELETE FROM incident_data_purge WHERE incident_id = ?", incidentId);
    jdbcTemplate.update("DELETE FROM incident WHERE id = ?", incidentId);
  }

  @Test
  @DisplayName("같은 전송 작업의 순번을 다시 요청하면 처음 확정한 순번을 반환한다")
  void repeated_assignment_returns_committed_sequence_without_incrementing_counter() {
    // given: 업무 트랜잭션에서 저장한 순번 미확정 작업이다.
    EventDispatchJob job = insertJob();

    // when: 별도 서비스 트랜잭션으로 순번을 확정하고 다시 요청한다.
    long first = service.getOrAssignSseSequence(job.getId());
    long repeated = service.getOrAssignSseSequence(job.getId());

    // then: 순번은 한 번만 증가하고 작업 내용과 업무 버전은 바뀌지 않는다.
    assertThat(first).isEqualTo(1L);
    assertThat(repeated).isEqualTo(first);
    assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(1L);
    assertThat(mapper.findById(job.getId()).getSseSequence()).isEqualTo(first);
    assertThat(mapper.findById(job.getId()).toPublishRequest()).isEqualTo(job.toPublishRequest());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT version FROM incident WHERE id = ?", Long.class, incidentId))
        .isEqualTo(7L);
  }

  @Test
  @DisplayName("동시에 같은 작업의 순번을 요청해도 둘 다 같은 순번을 받고 카운터는 한 번만 증가한다")
  void concurrent_assignment_returns_same_sequence_to_both_callers() throws Exception {
    // given: 같은 작업의 배정 요청 두 개가 DB 잠금 앞에서 겹치도록 준비한다.
    EventDispatchJob job = insertJob();
    var executor = Executors.newFixedThreadPool(2);
    var first = new AtomicReference<Future<Long>>();
    var second = new AtomicReference<Future<Long>>();
    try {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                jdbcTemplate.queryForObject(
                    "SELECT id FROM event_dispatch_job WHERE id = ? FOR UPDATE",
                    UUID.class,
                    job.getId());
                first.set(executor.submit(() -> service.getOrAssignSseSequence(job.getId())));
                second.set(executor.submit(() -> service.getOrAssignSseSequence(job.getId())));
                await()
                    .atMost(Duration.ofSeconds(10))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    jdbcTemplate.queryForObject(
                                        """
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND wait_event_type = 'Lock'
                  AND (query LIKE '%event_dispatch_job%' OR query LIKE '%UPDATE incident%')
                """,
                                        Integer.class))
                                .isEqualTo(2));
              });

      // when: 두 배정 요청이 실제 DB 트랜잭션을 끝낼 때까지 기다린다.
      long firstSequence = first.get().get(10, TimeUnit.SECONDS);
      long secondSequence = second.get().get(10, TimeUnit.SECONDS);

      // then: 한 요청이 실패하거나 새 순번을 만들지 않고 모두 같은 결과를 받는다.
      assertThat(firstSequence).isEqualTo(1L);
      assertThat(secondSequence).isEqualTo(firstSequence);
      assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(1L);
      assertThat(mapper.findById(job.getId()).getSseSequence()).isEqualTo(1L);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  @DisplayName("이벤트 순번 저장에 실패하면 사건 카운터 증가도 함께 롤백한다")
  void failed_sequence_save_rolls_back_incident_counter() {
    // given: DB 중복 제약 오류를 발생시키기 위해 카운터와 불일치하는 순번을 심는다.
    EventDispatchJob existing = insertJob();
    EventDispatchJob pending = insertJob();
    mapper.assignSseSequenceIfAbsent(existing.getId(), 1L);

    // when: 서비스가 카운터를 증가시킨 뒤 같은 순번을 저장하려다 실패한다.
    assertThatThrownBy(() -> service.getOrAssignSseSequence(pending.getId()))
        .isInstanceOf(DuplicateKeyException.class);

    // then: 서비스 트랜잭션이 양쪽 변경을 취소하고 미처리 이벤트는 남긴다.
    assertThat(incidentMapper.findLastSseSequence(incidentId)).isZero();
    assertThat(mapper.findById(pending.getId()).getSseSequence()).isNull();
    assertThat(mapper.findById(pending.getId()).getDispatchStatus()).isEqualTo("PENDING");
    assertThat(mapper.findById(pending.getId()).toPublishRequest())
        .isEqualTo(pending.toPublishRequest());
  }

  @Test
  @DisplayName("전환 전에 완료한 순번 없는 작업은 새 이력으로 편입하지 않는다")
  void completed_legacy_job_is_rejected_without_changing_counter_or_job() {
    // given: 메모리 방식으로 전송을 완료한 과거 작업이다.
    EventDispatchJob job = insertJob();
    mapper.claimById(job.getId(), "DISPATCHING");
    mapper.markCompleted(job.getId(), "COMPLETED");

    // when: 과거 완료 작업을 새 순번 배정 대상으로 넘긴다.
    assertThatThrownBy(() -> service.getOrAssignSseSequence(job.getId()))
        .isInstanceOf(IllegalStateException.class);

    // then: 과거 상태와 내용을 보존하고 새 사건 순번도 소비하지 않는다.
    assertThat(incidentMapper.findLastSseSequence(incidentId)).isZero();
    assertThat(mapper.findById(job.getId()).getSseSequence()).isNull();
    assertThat(mapper.findById(job.getId()).getDispatchStatus()).isEqualTo("COMPLETED");
    assertThat(mapper.findById(job.getId()).toPublishRequest()).isEqualTo(job.toPublishRequest());
  }

  @Test
  @DisplayName("DB 순번이 있는 작업은 완료 후에도 같은 순번으로 조회한다")
  void completed_sequenced_job_keeps_original_sequence() {
    // given: 새 방식으로 순번을 확정하고 서버 전송 처리까지 끝난 작업이다.
    EventDispatchJob job = insertJob();
    long sequence = service.getOrAssignSseSequence(job.getId());
    mapper.claimById(job.getId(), "DISPATCHING");
    mapper.markCompleted(job.getId(), "COMPLETED");

    // when / then: 완료 상태와 무관하게 원래 순번을 돌려준다.
    assertThat(service.getOrAssignSseSequence(job.getId())).isEqualTo(sequence);
    assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(sequence);
  }

  @Test
  @DisplayName("재전송의 마지막 순번은 별도 조회로 읽고 아직 커밋하지 않은 순번은 포함하지 않는다")
  void replay_end_sequence_excludes_uncommitted_assignment_without_changing_stored_data() {
    // given: 저장된 작업은 있지만 확정한 순번은 아직 없다.
    EventDispatchJob job = insertJob();
    assertThat(service.getSseReplayEndSequence(incidentId)).isZero();

    // when: 호출한 트랜잭션의 순번 저장이 커밋되지 않은 상태에서 재전송 끝 순번을 읽는다.
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              long sequence = incidentMapper.incrementAndGetSseSequence(incidentId);
              mapper.assignSseSequenceIfAbsent(job.getId(), sequence);
              assertThat(service.getSseReplayEndSequence(incidentId)).isZero();
              status.setRollbackOnly();
            });

    // then: 롤백될 순번을 재전송 범위에 넣지 않고 작업 원문·상태·카운터도 바꾸지 않는다.
    assertThat(service.getSseReplayEndSequence(incidentId)).isZero();
    assertThat(mapper.findById(job.getId()).getSseSequence()).isNull();
    assertThat(mapper.findById(job.getId()).getDispatchStatus()).isEqualTo("PENDING");
    assertThat(mapper.findById(job.getId()).toPublishRequest()).isEqualTo(job.toPublishRequest());
  }

  @Test
  @DisplayName("DB 재전송 이력을 지정한 개수로 나눠 읽고 조회 범위 뒤의 새 이벤트는 포함하지 않는다")
  void replay_pages_read_committed_range_without_including_newer_events() {
    // given: 메모리 저장소를 거치지 않고 DB에 두 이벤트와 순번을 확정했다.
    EventDispatchJob first = insertJob();
    EventDispatchJob second = insertJob();
    service.getOrAssignSseSequence(first.getId());
    service.getOrAssignSseSequence(second.getId());
    mapper.claimById(first.getId(), "DISPATCHING");
    mapper.markCompleted(first.getId(), "COMPLETED");
    mapper.claimById(second.getId(), "DISPATCHING");
    mapper.markFailed(second.getId(), "FAILED");

    // when: 조회할 마지막 순번을 DB에서 읽고, 페이지를 읽는 도중 새 이벤트가 추가된다.
    long throughSequence = service.getSseReplayEndSequence(incidentId);
    var firstPage = service.readSseReplayPage(incidentId, 0L, throughSequence, 1);
    service.getOrAssignSseSequence(insertJob().getId());
    var secondPage = service.readSseReplayPage(incidentId, 1L, throughSequence, 1);

    // then: 전송 상태와 무관하게 확정한 범위만 읽고 다음 순번이나 원문을 바꾸지 않는다.
    assertThat(throughSequence).isEqualTo(2L);
    assertThat(firstPage).extracting(EventDispatchJob::getId).containsExactly(first.getId());
    assertThat(secondPage).extracting(EventDispatchJob::getId).containsExactly(second.getId());
    assertThat(secondPage.get(0).toPublishRequest()).isEqualTo(second.toPublishRequest());
    assertThat(service.readSseReplayPage(incidentId, 2L, throughSequence, 1)).isEmpty();
    assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(3L);
    assertThat(mapper.findById(second.getId()).getDispatchStatus()).isEqualTo("FAILED");
  }

  @ParameterizedTest
  @CsvSource({"1,1", "2,2", "3,3", "0,3"})
  @DisplayName("이어받을 이력의 앞·중간·끝이나 전체가 없으면 일부 결과 대신 재조회를 요구한다")
  void missing_replay_sequence_requires_refetch_without_returning_partial_page(
      long deletedSequence, int limit) {
    // given: 순번 1~3은 확정됐지만 그중 일부 또는 전체 이력이 삭제됐다.
    for (int index = 0; index < 3; index++) {
      service.getOrAssignSseSequence(insertJob().getId());
    }
    if (deletedSequence == 0L) {
      jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", incidentId);
    } else {
      jdbcTemplate.update(
          "DELETE FROM event_dispatch_job WHERE incident_id = ? AND sse_sequence = ?",
          incidentId,
          deletedSequence);
    }

    // when / then: 다음에 와야 할 순번이 없으면 빈 목록이나 잘린 이력으로 성공 처리하지 않는다.
    assertThatThrownBy(() -> service.readSseReplayPage(incidentId, 0L, 3L, limit))
        .isInstanceOf(GoneRefetchRequiredException.class);
    assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(3L);
  }

  @ParameterizedTest
  @CsvSource({"-1,0,1", "2,1,1", "0,0,0", "0,0,-1"})
  @DisplayName("재전송 범위가 음수·역순이거나 조회 개수가 양수가 아니면 요청을 거부한다")
  void invalid_replay_range_or_limit_is_rejected(
      long afterSequence, long throughSequence, int limit) {
    // given: 호출부에서 잘못 지정한 범위 또는 조회 개수다.
    // when / then: 빈 페이지로 성공 처리하거나 잘못된 값을 SQL로 넘기지 않는다.
    assertThatThrownBy(
            () -> service.readSseReplayPage(incidentId, afterSequence, throughSequence, limit))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource({"4,4", "0,4", "9223372036854775807,9223372036854775807"})
  @DisplayName("DB에 확정되지 않은 순번으로 이어받으려 하면 재조회를 요구한다")
  void replay_range_beyond_committed_sequence_requires_refetch(
      long afterSequence, long throughSequence) {
    // given: 이력이 전혀 없는 사건의 0~0 범위는 비어 있고, 이후 순번 1~3을 확정한다.
    assertThat(service.readSseReplayPage(incidentId, 0L, 0L, 1)).isEmpty();
    for (int index = 0; index < 3; index++) {
      service.getOrAssignSseSequence(insertJob().getId());
    }

    // when / then: 현재 DB보다 앞선 순번을 빈 페이지나 정상 이력으로 받아들이지 않는다.
    assertThatThrownBy(
            () -> service.readSseReplayPage(incidentId, afterSequence, throughSequence, 1))
        .isInstanceOf(GoneRefetchRequiredException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CLOSED", "PURGED", "MISSING"})
  @DisplayName("종료·파기됐거나 존재하지 않는 사건의 과거 이력은 DB에 남아 있어도 조회하지 않는다")
  void closed_purged_or_missing_incident_rejects_history_read(String state) {
    // given: DB에 이력은 남았지만 더 이상 과거 내용을 조회할 수 없는 사건이다.
    EventDispatchJob job = insertJob();
    service.getOrAssignSseSequence(job.getId());
    if ("CLOSED".equals(state)) {
      incidentMapper.closeIncident(incidentId, null, Instant.now());
    } else if ("PURGED".equals(state)) {
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
    UUID requestedIncidentId = "MISSING".equals(state) ? UUID.randomUUID() : incidentId;

    // when / then: 과거 내용이나 정상적인 빈 페이지를 돌려주지 않고 호출부에 조회 불가를 알린다.
    assertThatThrownBy(() -> service.getSseReplayEndSequence(requestedIncidentId))
        .isInstanceOf(GoneRefetchRequiredException.class);
    assertThatThrownBy(() -> service.readSseReplayPage(requestedIncidentId, 0L, 1L, 1))
        .isInstanceOf(GoneRefetchRequiredException.class);
    assertThatThrownBy(() -> service.readSseReplayPage(requestedIncidentId, 1L, 1L, 1))
        .isInstanceOf(GoneRefetchRequiredException.class);
    assertThat(mapper.findById(job.getId()).toPublishRequest()).isEqualTo(job.toPublishRequest());
  }

  private EventDispatchJob insertJob() {
    UUID sourceId = UUID.randomUUID();
    EventDispatchJob job =
        EventDispatchJob.from(
            new PublishRequest(
                UUID.randomUUID(),
                incidentId,
                "PATH_APPENDED",
                1,
                "search_path",
                sourceId,
                Instant.parse("2026-09-26T00:00:00Z"),
                Map.of("id", sourceId.toString(), "status", "RECORDING", "version", 1)));
    mapper.insert(job);
    return job;
  }
}
