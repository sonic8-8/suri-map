package com.surimap.eventhub.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.surimap.eventhub.dto.PublishRequest;
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
