package com.surimap.eventhub.adapter;

import com.surimap.eventhub.stream.GoneRefetchRequiredException;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.retention.purge.IncidentDataPurgeStatus;
import com.surimap.retention.purge.PurgeRunMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class EventDispatchJobService {

  private final EventDispatchJobMapper mapper;
  private final IncidentMapper incidentMapper;
  private final PurgeRunMapper purgeRunMapper;

  public int requeueInterruptedJobs() {
    return mapper.requeueInterruptedJobs();
  }

  public int requeueFailedJobs() {
    return mapper.requeueFailedJobs();
  }

  public List<EventDispatchJob> claimPendingJobs(int limit) {
    return mapper.claimPending(Math.max(1, limit), "DISPATCHING");
  }

  /** 이번 재전송의 마지막 확정 순번을 읽는다. 호출부는 연결 등록·실시간 이벤트 대기를 조율하고 이 값을 모든 페이지의 throughSequence로 유지한다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public long getSseReplayEndSequence(UUID incidentId) {
    Long lastSequence = incidentMapper.findLastSseSequence(incidentId);
    if (lastSequence == null
        || incidentMapper
            .findByIncidentId(incidentId)
            .filter(incident -> "OPEN".equals(incident.getStatus()))
            .isEmpty()
        || purgeRunMapper
            .findByIncidentId(incidentId)
            .filter(run -> run.getStatus() == IncidentDataPurgeStatus.COMPLETED)
            .isPresent()) {
      throw new GoneRefetchRequiredException();
    }
    return lastSequence;
  }

  /**
   * afterSequence 다음부터 throughSequence까지 연속된 이력을 최대 limit개 읽는다. 최초 접속의 시작 위치·계정 권한·종료 정보 응답과 실제 SSE
   * 전송은 호출 경로에서 별도로 처리한다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public List<EventDispatchJob> readSseReplayPage(
      UUID incidentId, long afterSequence, long throughSequence, int limit) {
    List<EventDispatchJob> jobs =
        readRetainedSseReplayPage(incidentId, afterSequence, throughSequence, limit);
    validateReplayContinuity(jobs, afterSequence, throughSequence, limit);
    return jobs;
  }

  /** 마지막 수신 순번 없는 새 연결은 누락 복구 대신 현재 남은 이력을 읽는다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public List<EventDispatchJob> readRetainedSseReplayPage(
      UUID incidentId, long afterSequence, long throughSequence, int limit) {
    validateReplayRange(afterSequence, throughSequence, limit);
    if (throughSequence > getSseReplayEndSequence(incidentId)) {
      throw new GoneRefetchRequiredException();
    }
    return mapper.findBySseSequenceRange(incidentId, afterSequence, throughSequence, limit);
  }

  /** 응답 시작 전에 뒤 페이지의 누락도 확인한다. 순번의 양수·사건별 유일성은 DB 제약이 보장한다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void validateSseReplayContinuity(
      UUID incidentId, long afterSequence, long throughSequence) {
    validateReplayRange(afterSequence, throughSequence, 1);
    if (throughSequence > getSseReplayEndSequence(incidentId)
        || mapper.countBySseSequenceRange(incidentId, afterSequence, throughSequence)
            != throughSequence - afterSequence) {
      throw new GoneRefetchRequiredException();
    }
  }

  /** 종료된 사건은 과거 내용 없이 확정된 종료 알림만 허용한다. 파기 완료·누락 사건은 재조회한다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public EventDispatchJob readIncidentClosedEvent(UUID incidentId) {
    if (incidentMapper
            .findByIncidentId(incidentId)
            .filter(incident -> "CLOSED".equals(incident.getStatus()))
            .isEmpty()
        || purgeRunMapper
            .findByIncidentId(incidentId)
            .filter(run -> run.getStatus() == IncidentDataPurgeStatus.COMPLETED)
            .isPresent()) {
      throw new GoneRefetchRequiredException();
    }
    EventDispatchJob event = mapper.findLatestSequencedIncidentClosedEvent(incidentId);
    if (event == null) {
      throw new GoneRefetchRequiredException();
    }
    return event;
  }

  public long getOrAssignSseSequence(UUID jobId) {
    EventDispatchJob job = mapper.findByIdForUpdate(jobId);
    if (job == null) {
      throw new IllegalArgumentException("event dispatch job not found: " + jobId);
    }
    if (job.getSseSequence() != null) {
      return job.getSseSequence();
    }
    if ("COMPLETED".equals(job.getDispatchStatus())) {
      throw new IllegalStateException("completed legacy job must remain unsequenced: " + jobId);
    }
    Long sequence = incidentMapper.incrementAndGetSseSequence(job.getIncidentId());
    if (sequence == null || mapper.assignSseSequenceIfAbsent(jobId, sequence) != 1) {
      throw new IllegalStateException("failed to assign SSE sequence: " + jobId);
    }
    return sequence;
  }

  public void completeJob(UUID jobId) {
    if (mapper.markCompleted(jobId, "COMPLETED") != 1) {
      throw new IllegalStateException("dispatching job not found: " + jobId);
    }
  }

  public void failJob(UUID jobId) {
    if (mapper.markFailed(jobId, "FAILED") != 1) {
      throw new IllegalStateException("dispatching job not found: " + jobId);
    }
  }

  private void validateReplayRange(long afterSequence, long throughSequence, int limit) {
    if (afterSequence < 0 || throughSequence < afterSequence || limit <= 0) {
      throw new IllegalArgumentException("invalid SSE replay range or limit");
    }
  }

  private void validateReplayContinuity(
      List<EventDispatchJob> jobs, long afterSequence, long throughSequence, int limit) {
    long expectedCount = Math.min((long) limit, throughSequence - afterSequence);
    if (jobs.size() != expectedCount) {
      throw new GoneRefetchRequiredException();
    }
    long expectedSequence = afterSequence;
    for (EventDispatchJob job : jobs) {
      expectedSequence++;
      if (job.getSseSequence() != expectedSequence) {
        throw new GoneRefetchRequiredException();
      }
    }
  }
}
