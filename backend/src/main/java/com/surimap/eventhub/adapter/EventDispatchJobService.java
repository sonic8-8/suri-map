package com.surimap.eventhub.adapter;

import com.surimap.incident.repository.IncidentMapper;
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

  public int requeueInterruptedJobs() {
    return mapper.requeueInterruptedJobs();
  }

  public int requeueFailedJobs() {
    return mapper.requeueFailedJobs();
  }

  public List<EventDispatchJob> claimPendingJobs(int limit) {
    return mapper.claimPending(Math.max(1, limit), "DISPATCHING");
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
}
