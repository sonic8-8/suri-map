package com.surimap.eventhub.adapter;

import com.surimap.eventhub.stream.SseStreamService;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@ConditionalOnProperty(
    name = "surimap.eventhub.dispatch.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EventDispatchJobDispatcher {

  static final String STATUS_DISPATCHING = "DISPATCHING";
  static final String STATUS_COMPLETED = "COMPLETED";
  static final String STATUS_FAILED = "FAILED";

  private static final Logger log = LoggerFactory.getLogger(EventDispatchJobDispatcher.class);

  private final EventDispatchJobMapper mapper;
  private final SseStreamService sseStreamService;

  public EventDispatchJobDispatcher(
      EventDispatchJobMapper mapper, SseStreamService sseStreamService) {
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    this.sseStreamService =
        Objects.requireNonNull(sseStreamService, "sseStreamService must not be null");
  }

  public void dispatchAfterCommit(UUID eventDispatchJobId) {
    Objects.requireNonNull(eventDispatchJobId, "eventDispatchJobId must not be null");
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              dispatchJob(eventDispatchJobId);
            }
          });
      return;
    }

    dispatchJob(eventDispatchJobId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean dispatchJob(UUID eventDispatchJobId) {
    EventDispatchJobDispatchRecord row =
        mapper.claimById(eventDispatchJobId, STATUS_DISPATCHING);
    if (row == null) {
      return false;
    }
    return dispatchClaimed(row);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int dispatchPendingBatch(int limit) {
    int normalizedLimit = Math.max(1, limit);
    int dispatched = 0;
    for (EventDispatchJobDispatchRecord row :
        mapper.claimPending(normalizedLimit, STATUS_DISPATCHING)) {
      if (dispatchClaimed(row)) {
        dispatched++;
      }
    }
    return dispatched;
  }

  private boolean dispatchClaimed(EventDispatchJobDispatchRecord row) {
    try {
      sseStreamService.dispatchLive(row.id(), row.toPublishRequest());
      mapper.markCompleted(row.id(), STATUS_COMPLETED);
      return true;
    } catch (RuntimeException exception) {
      mapper.markFailed(row.id(), STATUS_FAILED);
      log.warn(
          "event_dispatch_job SSE dispatch failed. jobId={}, eventId={}, eventType={}",
          row.id(),
          row.eventId(),
          row.eventType(),
          exception);
      return false;
    }
  }
}
