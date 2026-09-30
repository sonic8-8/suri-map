package com.surimap.global.sse;

import com.surimap.global.event.EventPublishRequest;
import com.surimap.global.event.EventPublishRequestValidator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
public class ServerSentEventJobWorker implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(ServerSentEventJobWorker.class);

  private final ServerSentEventJobService service;
  private static final String INCIDENT_CLOSED = "INCIDENT_CLOSED";
  private static final String INCIDENT_PURGED = "INCIDENT_PURGED";
  private final ServerSentEventConnectionRegistry connectionRegistry;
  private final boolean pollingEnabled;
  private final long initialDelayMs;
  private final long fixedDelayMs;
  private final int batchSize;
  // DB 작업·순번 배정은 직렬 처리하고, 실제 응답 쓰기는 연결별 컨테이너 작업에 맡긴다.
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

  public ServerSentEventJobWorker(
      ServerSentEventJobService service,
      ServerSentEventConnectionRegistry connectionRegistry,
      @Value("${surimap.eventhub.dispatch.polling-enabled:true}") boolean pollingEnabled,
      @Value("${surimap.eventhub.dispatch.initial-delay-ms:1000}") long initialDelayMs,
      @Value("${surimap.eventhub.dispatch.fixed-delay-ms:1000}") long fixedDelayMs,
      @Value("${surimap.eventhub.dispatch.batch-size:100}") int batchSize) {
    this.service = service;
    this.connectionRegistry = connectionRegistry;
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
        List<ServerSentEventJob> jobs = service.claimPendingJobs(batchSize - processed);
        if (jobs.isEmpty()) {
          break;
        }
        for (ServerSentEventJob job : jobs) {
          dispatchJob(job);
          processed++;
        }
      }
    } catch (RuntimeException exception) {
      log.warn("event_dispatch_job worker failed", exception);
    }
  }

  private void dispatchJob(ServerSentEventJob job) {
    try {
      long sequence = service.getOrAssignServerSentEventSequence(job.getId());
      // 프록시를 거친 서비스 호출이 커밋된 뒤, DB 트랜잭션 밖에서 전송한다.
      dispatchLiveEvent(job.toPublishRequest(), sequence);
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

  private ServerSentEventMessage dispatchLiveEvent(
      EventPublishRequest request, long serverSentEventSequence) {
    EventPublishRequestValidator.validate(request);
    if (serverSentEventSequence <= 0) {
      throw new IllegalArgumentException("serverSentEventSequence must be positive");
    }
    boolean terminal =
        INCIDENT_CLOSED.equals(request.getType()) || INCIDENT_PURGED.equals(request.getType());
    UUID incidentId = request.getIncidentId();
    String eventType = request.getType();
    var message =
        ServerSentEventMessage.builder()
            .id(Long.toString(serverSentEventSequence))
            .event(request.getType())
            .data(request)
            .build();
    // 대기 중에는 직렬화한 데이터 외에 원래 payload까지 다시 붙잡아 두지 않는다.
    Runnable validateBeforeSend = () -> validateEventTransmission(incidentId, eventType);
    validateBeforeSend.run();
    // 이력 저장 성공은 전송 성공이 아니다. 재시도도 같은 순번으로 전달한다.
    connectionRegistry.sendToIncident(request.getIncidentId(), message, validateBeforeSend);
    extractRecipientAccountIds(request)
        .forEach(
            accountId -> connectionRegistry.sendToAccount(accountId, message, validateBeforeSend));
    if (terminal) {
      connectionRegistry.closeIncidentConnections(request.getIncidentId());
    }
    return message;
  }

  private void validateEventTransmission(UUID incidentId, String eventType) {
    if (!INCIDENT_CLOSED.equals(eventType) && !INCIDENT_PURGED.equals(eventType)) {
      // 대기열에 넣을 때의 OPEN 확인만으로 나중의 쓰기를 허용하지 않는다.
      // 이미 시작한 응답 쓰기를 취소하거나 DB 커밋과 네트워크 쓰기를 원자화하지는 않는다.
      service.validateServerSentEventTransmission(incidentId);
    }
  }

  private List<UUID> extractRecipientAccountIds(EventPublishRequest request) {
    Object value =
        "INCIDENT_CREATED".equals(request.getType())
            ? request.getPayload().get("memberAccountIds")
            : request.getPayload().get("changedAccountIds");
    if (!(value instanceof List<?> values)) {
      return List.of();
    }
    return values.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .map(this::parseUuidOrNull)
        .filter(Objects::nonNull)
        .toList();
  }

  private UUID parseUuidOrNull(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
