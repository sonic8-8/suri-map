package com.surimap.eventhub.adapter;

import com.surimap.eventhub.stream.SseStreamService;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "surimap.eventhub.dispatch.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EventDispatchJobWorker implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(EventDispatchJobWorker.class);

  private final EventDispatchJobService service;
  private final SseStreamService sseStreamService;
  private final boolean pollingEnabled;
  private final long initialDelayMs;
  private final long fixedDelayMs;
  private final int batchSize;
  // ponytail: 기존 단일 전송 스레드를 유지한다. 느린 연결 격리는 연결별 대기량 제한과 함께 다룬다.
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "event-dispatch-job-worker");
            thread.setDaemon(true);
            return thread;
          });
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean wakeQueued = new AtomicBoolean(false);
  private ScheduledFuture<?> future;

  public EventDispatchJobWorker(
      EventDispatchJobService service,
      SseStreamService sseStreamService,
      @Value("${surimap.eventhub.dispatch.polling-enabled:true}") boolean pollingEnabled,
      @Value("${surimap.eventhub.dispatch.initial-delay-ms:1000}") long initialDelayMs,
      @Value("${surimap.eventhub.dispatch.fixed-delay-ms:1000}") long fixedDelayMs,
      @Value("${surimap.eventhub.dispatch.batch-size:100}") int batchSize) {
    this.service = service;
    this.sseStreamService = sseStreamService;
    this.pollingEnabled = pollingEnabled;
    this.initialDelayMs = Math.max(0L, initialDelayMs);
    this.fixedDelayMs = Math.max(100L, fixedDelayMs);
    this.batchSize = Math.max(1, batchSize);
  }

  @Override
  public void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    try {
      // ponytail: 현재 배포 설정은 백엔드 1개다. 동시 다중 인스턴스 운영 전에는 소유권·회수 방식을 바꾼다.
      int recovered = service.requeueInterruptedJobs();
      if (pollingEnabled) {
        future =
            executor.scheduleWithFixedDelay(
                this::recoverUnfinishedJobsAndDispatch,
                initialDelayMs,
                fixedDelayMs,
                TimeUnit.MILLISECONDS);
      }
      if (recovered > 0) {
        log.info("event_dispatch_job interrupted jobs requeued. count={}", recovered);
        wake();
      }
    } catch (RuntimeException exception) {
      running.set(false);
      throw exception;
    }
  }

  @Override
  public void stop() {
    running.set(false);
    if (future != null) {
      future.cancel(false);
    }
    executor.shutdownNow();
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  public void wake() {
    if (!running.get() || !wakeQueued.compareAndSet(false, true)) {
      return;
    }
    try {
      executor.execute(
          () -> {
            wakeQueued.set(false);
            dispatchSafely();
          });
    } catch (RejectedExecutionException exception) {
      wakeQueued.set(false);
      // 업무 저장은 이미 커밋됐다. 신호를 놓친 작업은 DB에 남겨 주기 조회가 처리한다.
      log.warn("event_dispatch_job worker wake rejected. running={}", running.get());
    }
  }

  private void recoverUnfinishedJobsAndDispatch() {
    if (!running.get()) {
      return;
    }
    try {
      // 전송과 같은 단일 스레드에서 실행하므로, 이전 회차의 결과 저장 실패도 회수할 수 있다.
      int interrupted = service.requeueInterruptedJobs();
      int failed = service.requeueFailedJobs();
      if (interrupted > 0 || failed > 0) {
        log.info(
            "event_dispatch_job unfinished jobs requeued. interrupted={}, failed={}",
            interrupted,
            failed);
      }
      dispatchSafely();
    } catch (RuntimeException exception) {
      log.warn("event_dispatch_job retry scan failed", exception);
    }
  }

  private void dispatchSafely() {
    if (!running.get()) {
      return;
    }
    try {
      int processed = 0;
      while (running.get() && processed < batchSize) {
        List<EventDispatchJob> jobs = service.claimPendingJobs(batchSize - processed);
        if (jobs.isEmpty()) {
          break;
        }
        for (EventDispatchJob job : jobs) {
          dispatchJob(job);
          processed++;
        }
      }
    } catch (RuntimeException exception) {
      log.warn("event_dispatch_job worker failed", exception);
    }
  }

  private void dispatchJob(EventDispatchJob job) {
    try {
      long sequence = service.getOrAssignSseSequence(job.getId());
      // 프록시를 거친 서비스 호출이 커밋된 뒤, DB 트랜잭션 밖에서 전송한다.
      sseStreamService.dispatchLive(job.getId(), job.toPublishRequest(), sequence);
      service.completeJob(job.getId());
    } catch (RuntimeException exception) {
      log.warn(
          "event_dispatch_job SSE dispatch failed. jobId={}, eventId={}, eventType={}",
          job.getId(),
          job.getEventId(),
          job.getEventType(),
          exception);
      service.failJob(job.getId());
    }
  }
}
