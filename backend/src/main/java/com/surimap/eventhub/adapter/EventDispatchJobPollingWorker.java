package com.surimap.eventhub.adapter;

import java.util.concurrent.Executors;
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
    name = {"surimap.eventhub.dispatch.enabled", "surimap.eventhub.dispatch.polling-enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class EventDispatchJobPollingWorker implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(EventDispatchJobPollingWorker.class);

  private final EventDispatchJobDispatcher dispatcher;
  private final long initialDelayMs;
  private final long fixedDelayMs;
  private final int batchSize;
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "event-dispatch-job-worker");
            thread.setDaemon(true);
            return thread;
          });
  private final AtomicBoolean running = new AtomicBoolean(false);
  private ScheduledFuture<?> future;

  public EventDispatchJobPollingWorker(
      EventDispatchJobDispatcher dispatcher,
      @Value("${surimap.eventhub.dispatch.initial-delay-ms:1000}") long initialDelayMs,
      @Value("${surimap.eventhub.dispatch.fixed-delay-ms:1000}") long fixedDelayMs,
      @Value("${surimap.eventhub.dispatch.batch-size:100}") int batchSize) {
    this.dispatcher = dispatcher;
    this.initialDelayMs = Math.max(0L, initialDelayMs);
    this.fixedDelayMs = Math.max(100L, fixedDelayMs);
    this.batchSize = Math.max(1, batchSize);
  }

  @Override
  public void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    future =
        executor.scheduleWithFixedDelay(
            this::dispatchSafely, initialDelayMs, fixedDelayMs, TimeUnit.MILLISECONDS);
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

  private void dispatchSafely() {
    if (!running.get()) {
      return;
    }
    try {
      dispatcher.dispatchPendingBatch(batchSize);
    } catch (RuntimeException exception) {
      log.warn("event_dispatch_job polling dispatch failed", exception);
    }
  }
}
