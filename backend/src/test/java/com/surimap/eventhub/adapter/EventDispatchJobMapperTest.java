package com.surimap.eventhub.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Transactional
class EventDispatchJobMapperTest extends PostGisIntegrationTestSupport {

  @Autowired private EventDispatchJobMapper mapper;
  @Autowired private IncidentMapper incidentMapper;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("이벤트에 저장한 순번은 재배정으로 덮어쓰지 않고 원래 내용과 함께 조회한다")
  void assign_sse_sequence_preserves_event_and_does_not_overwrite_existing_sequence() {
    // given: 사건과 순번 미확정 전송 작업이 저장돼 있다.
    UUID incidentId = insertIncident();
    EventDispatchJob job = insertJob(incidentId);
    EventDispatchJob pending = mapper.findById(job.getId());
    assertThat(pending.getSseSequence()).isNull();
    assertThat(pending.getDispatchStatus()).isEqualTo("PENDING");

    // when: 사건의 다음 순번을 저장한 뒤 다른 순번으로 재배정을 시도한다.
    long sequence = incidentMapper.incrementAndGetSseSequence(incidentId);
    assertThat(mapper.assignSseSequenceIfAbsent(job.getId(), sequence)).isEqualTo(1);
    assertThat(mapper.assignSseSequenceIfAbsent(job.getId(), 2L)).isZero();

    // then: 처음 저장한 순번과 이벤트 내용, 전송 대기 상태를 유지한다.
    EventDispatchJob saved = mapper.findById(job.getId());
    assertThat(saved.getSseSequence()).isEqualTo(1L);
    assertThat(saved.toPublishRequest()).isEqualTo(job.toPublishRequest());
    assertThat(saved.getDispatchStatus()).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("같은 사건의 순번 중복은 DB가 거부하고 다른 사건에는 같은 순번을 허용한다")
  void duplicate_sse_sequence_is_rejected_only_within_same_incident() {
    // given: 서로 다른 사건의 이벤트와 같은 사건의 추가 이벤트를 저장한다.
    UUID incidentId = insertIncident();
    EventDispatchJob first = insertJob(incidentId);
    EventDispatchJob duplicate = insertJob(incidentId);
    EventDispatchJob otherIncident = insertJob(insertIncident());

    // when: 서로 다른 사건에 같은 순번을 저장한다.
    assertThat(mapper.assignSseSequenceIfAbsent(first.getId(), 1L)).isEqualTo(1);
    assertThat(mapper.assignSseSequenceIfAbsent(otherIncident.getId(), 1L)).isEqualTo(1);

    // then: 같은 사건의 다른 이벤트에 같은 순번을 넣으면 DB에서 거부한다.
    assertThatThrownBy(() -> mapper.assignSseSequenceIfAbsent(duplicate.getId(), 1L))
        .isInstanceOf(DuplicateKeyException.class);
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, -1L})
  @DisplayName("이벤트 순번이 0 이하이면 DB가 저장을 거부한다")
  void non_positive_sse_sequence_is_rejected(long sequence) {
    // given: 아직 순번을 배정하지 않은 이벤트가 있다.
    EventDispatchJob job = insertJob(insertIncident());

    // when / then: 0이나 음수를 저장하려 하면 DB 제약에 걸린다.
    assertThatThrownBy(() -> mapper.assignSseSequenceIfAbsent(job.getId(), sequence))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("전환 전 완료한 작업은 보존하되 새 순번을 배정하지 않는다")
  void completed_job_without_sse_sequence_remains_unassigned() {
    // given: 옛 전송 방식으로 이미 완료했고 DB SSE 순번은 없는 작업이다.
    EventDispatchJob job = insertJob(insertIncident());
    mapper.claimById(job.getId(), "DISPATCHING");
    assertThat(mapper.markCompleted(job.getId(), "COMPLETED")).isEqualTo(1);

    // when: 기존 완료 작업에 새 순번 배정을 시도한다.
    assertThat(mapper.assignSseSequenceIfAbsent(job.getId(), 1L)).isZero();

    // then: 행을 삭제하거나 상태·이벤트 내용을 바꾸지 않고 순번을 비워 둔다.
    EventDispatchJob saved = mapper.findById(job.getId());
    assertThat(saved.getSseSequence()).isNull();
    assertThat(saved.getDispatchStatus()).isEqualTo("COMPLETED");
    assertThat(saved.toPublishRequest()).isEqualTo(job.toPublishRequest());
  }

  @Test
  @DisplayName("사건의 재전송 이력은 순번 범위와 개수만큼 오름차순으로 조회한다")
  void history_is_ordered_and_bounded_by_incident_sequence_range_and_limit() {
    // given: 저장 순서와 순번이 다르며 다른 사건·순번 미확정 작업도 섞여 있다.
    UUID incidentId = insertIncident();
    EventDispatchJob third = insertJob(incidentId);
    EventDispatchJob first = insertJob(incidentId);
    EventDispatchJob second = insertJob(incidentId);
    EventDispatchJob unassigned = insertJob(incidentId);
    EventDispatchJob otherIncident = insertJob(insertIncident());
    mapper.assignSseSequenceIfAbsent(third.getId(), 3L);
    mapper.assignSseSequenceIfAbsent(first.getId(), 1L);
    mapper.assignSseSequenceIfAbsent(second.getId(), 2L);
    mapper.assignSseSequenceIfAbsent(otherIncident.getId(), 1L);
    mapper.claimById(second.getId(), "DISPATCHING");
    mapper.markCompleted(second.getId(), "COMPLETED");
    mapper.claimById(third.getId(), "DISPATCHING");
    mapper.markFailed(third.getId(), "FAILED");
    mapper.claimById(unassigned.getId(), "DISPATCHING");
    mapper.markCompleted(unassigned.getId(), "COMPLETED");

    // when: 순번 범위와 조회 개수를 지정해 재전송 이력을 읽는다.
    List<EventDispatchJob> history = mapper.findBySseSequenceRange(incidentId, 0L, 3L, 3);

    // then: 순번 없는 옛 완료 작업·다른 사건은 제외하고 전송 상태와 무관하게 순서대로 읽는다.
    assertThat(history).extracting(EventDispatchJob::getSseSequence).containsExactly(1L, 2L, 3L);
    assertThat(history)
        .extracting(EventDispatchJob::getId)
        .containsExactly(first.getId(), second.getId(), third.getId());
    assertThat(mapper.findBySseSequenceRange(incidentId, 1L, 3L, 1))
        .extracting(EventDispatchJob::getId)
        .containsExactly(second.getId());
    assertThat(mapper.findBySseSequenceRange(incidentId, 2L, 3L, 1))
        .extracting(EventDispatchJob::getId)
        .containsExactly(third.getId());
    assertThat(mapper.findBySseSequenceRange(incidentId, 0L, 2L, 3))
        .extracting(EventDispatchJob::getId)
        .containsExactly(first.getId(), second.getId());
    assertThat(mapper.findBySseSequenceRange(incidentId, 3L, 3L, 3)).isEmpty();
    assertThat(mapper.findById(unassigned.getId()).getSseSequence()).isNull();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("순번 저장을 롤백하면 직전 커밋의 사건 순번과 이벤트 기록을 유지한다")
  void sequence_transaction_rollback_preserves_previously_committed_state() {
    // given: 첫 이벤트의 순번은 커밋했고 다음 이벤트는 순번을 기다린다.
    UUID incidentId = insertIncident();
    EventDispatchJob committed = insertJob(incidentId);
    EventDispatchJob pending = insertJob(incidentId);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    try {
      transaction.executeWithoutResult(
          status -> {
            long sequence = incidentMapper.incrementAndGetSseSequence(incidentId);
            assertThat(mapper.assignSseSequenceIfAbsent(committed.getId(), sequence)).isEqualTo(1);
          });

      // when: 다음 순번을 양쪽에 저장한 뒤 오류가 발생해 트랜잭션을 롤백한다.
      assertThatThrownBy(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        long sequence = incidentMapper.incrementAndGetSseSequence(incidentId);
                        assertThat(sequence).isEqualTo(2L);
                        assertThat(mapper.assignSseSequenceIfAbsent(pending.getId(), sequence))
                            .isEqualTo(1);
                        throw new IllegalStateException("roll back sequence assignment");
                      }))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("roll back sequence assignment");

      // then: 새 DB 조회에서도 커밋한 1번만 남고, 미확정 이벤트 내용은 보존된다.
      assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(1L);
      assertThat(mapper.findById(committed.getId()).getSseSequence()).isEqualTo(1L);
      assertThat(mapper.findById(pending.getId()).getSseSequence()).isNull();
      assertThat(mapper.findById(pending.getId()).toPublishRequest())
          .isEqualTo(pending.toPublishRequest());
      assertThat(mapper.findBySseSequenceRange(incidentId, 0L, 2L, 2))
          .extracting(EventDispatchJob::getId)
          .containsExactly(committed.getId());
    } finally {
      jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", incidentId);
      jdbcTemplate.update("DELETE FROM incident WHERE id = ?", incidentId);
    }
  }

  @Test
  @DisplayName("작업을 선점하면 같은 트랜잭션의 조회에 반영되고 다시 선점되지 않는다")
  void claimed_jobs_are_visible_to_subsequent_reads_and_not_claimed_twice() {
    // given: 선점 전에 두 작업의 대기 상태를 조회했다.
    UUID incidentId = insertIncident();
    EventDispatchJob byId = insertJob(incidentId);
    EventDispatchJob byBatch = insertJob(incidentId);
    assertThat(mapper.findById(byId.getId()).getDispatchStatus()).isEqualTo("PENDING");
    assertThat(mapper.findById(byBatch.getId()).getDispatchStatus()).isEqualTo("PENDING");

    // when: 작업 하나를 ID로 선점한다.
    assertThat(mapper.claimById(byId.getId(), "DISPATCHING").getId()).isEqualTo(byId.getId());

    // then: 조회 캐시가 갱신돼 새 상태가 보이고 같은 작업을 다시 선점하지 못한다.
    assertThat(mapper.findById(byId.getId()).getDispatchStatus()).isEqualTo("DISPATCHING");
    assertThat(mapper.claimById(byId.getId(), "DISPATCHING")).isNull();

    // when: 남은 작업을 묶음 조회로 선점한다.
    assertThat(mapper.claimPending(1, "DISPATCHING"))
        .extracting(EventDispatchJob::getId)
        .containsExactly(byBatch.getId());

    // then: 묶음 선점도 새 상태로 조회되며 같은 결과를 캐시에서 반복하지 않는다.
    assertThat(mapper.findById(byBatch.getId()).getDispatchStatus()).isEqualTo("DISPATCHING");
    assertThat(mapper.claimPending(1, "DISPATCHING")).isEmpty();
  }

  private UUID insertIncident() {
    UUID incidentId = UUID.randomUUID();
    Instant now = Instant.parse("2026-09-26T00:00:00Z");
    incidentMapper.insertIncident(
        incidentId, UUID.randomUUID(), "SSE sequence test", "OPEN", now, 1L, now);
    return incidentId;
  }

  private EventDispatchJob insertJob(UUID incidentId) {
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
