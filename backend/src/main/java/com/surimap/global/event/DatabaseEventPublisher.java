package com.surimap.global.event;

import com.surimap.global.sse.ServerSentEventJob;
import com.surimap.global.sse.ServerSentEventJobMapper;
import com.surimap.global.sse.ServerSentEventJobWorker;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DB 기반 EventPublisher 실구현체.
 *
 * <p>S4.json §three_stack_deliverables.backend.modules[1]: EventPublisher port 실구현.
 * EventPublishRequest를 event_dispatch_job row로 caller domain transaction 안에서 원자적으로 저장한다.
 *
 * <p>AC-S4-01: domain row, event_dispatch_job row가 같은 eventId로 원자적으로 보존된다. AC-S4-07: insert 실패 시
 * caller transaction이 rollback된다.
 *
 * <p>CapturingEventPublisher는 {@code PolicePhoneHeartbeatConfig}의
 * {@code @ConditionalOnMissingBean(EventPublisher.class)} 조건으로 등록되므로, 이 빈이 존재하면
 * CapturingEventPublisher fallback은 비활성화된다.
 */
@Component
@Slf4j
public class DatabaseEventPublisher implements EventPublisher {

  private final ServerSentEventJobMapper mapper;
  private final Supplier<List<DomainEventConsumer>> domainEventConsumers;
  private final ServerSentEventJobWorker worker;

  @Autowired
  public DatabaseEventPublisher(
      ServerSentEventJobMapper mapper,
      ObjectProvider<DomainEventConsumer> domainEventConsumerProvider,
      ObjectProvider<ServerSentEventJobWorker> workerProvider) {
    this(
        mapper,
        () -> domainEventConsumerProvider.orderedStream().toList(),
        workerProvider.getIfAvailable());
  }

  public DatabaseEventPublisher(ServerSentEventJobMapper mapper) {
    this(mapper, List::of, null);
  }

  DatabaseEventPublisher(
      ServerSentEventJobMapper mapper, Supplier<List<DomainEventConsumer>> domainEventConsumers) {
    this(mapper, domainEventConsumers, null);
  }

  DatabaseEventPublisher(
      ServerSentEventJobMapper mapper,
      Supplier<List<DomainEventConsumer>> domainEventConsumers,
      ServerSentEventJobWorker worker) {
    this.mapper = mapper;
    this.domainEventConsumers = domainEventConsumers == null ? List::of : domainEventConsumers;
    this.worker = worker;
  }

  /**
   * EventPublishRequest를 event_dispatch_job 테이블에 INSERT한다.
   *
   * <p>caller의 트랜잭션에 참여하므로 caller rollback 시 row도 함께 취소된다 (AC-S4-07). event_id UNIQUE constraint로
   * 동일 eventId 중복 publish를 방지한다 (S4.json duplicate_event_dedupe).
   *
   * @param request 발행할 이벤트 요청
   */
  @Override
  public void publish(EventPublishRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(request.getEventId(), "eventId must not be null");
    Objects.requireNonNull(request.getIncidentId(), "incidentId must not be null");
    Objects.requireNonNull(request.getType(), "type must not be null");
    Objects.requireNonNull(request.getPayload(), "payload must not be null");

    ServerSentEventJob job = ServerSentEventJob.from(request);
    mapper.insert(job);
    recordEventStage("outbox_insert_returned", request.getEventId(), job.getId());
    dispatchLocalConsumers(request);
    wakeWorkerAfterCommit(request.getEventId(), job.getId());
  }

  private static void recordEventStage(String stage, UUID eventId, UUID jobId) {
    if (log.isDebugEnabled()) {
      log.debug(
          "board_event stage={} eventId={} jobId={} wallTimeMs={} monotonicNs={}",
          stage,
          eventId,
          jobId,
          System.currentTimeMillis(),
          System.nanoTime());
    }
  }

  private void dispatchLocalConsumers(EventPublishRequest request) {
    domainEventConsumers.get().stream()
        .filter(consumer -> consumer.supports(request))
        .forEach(consumer -> consumer.consume(request));
  }

  private void wakeWorkerAfterCommit(UUID eventId, UUID jobId) {
    if (worker == null && !log.isDebugEnabled()) {
      return;
    }
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      boolean actualTransaction = TransactionSynchronizationManager.isActualTransactionActive();
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              if (actualTransaction) {
                // DB의 정확한 commit timestamp가 아니라 커밋 후 콜백 관측 시각이다.
                recordEventStage("transaction_after_commit", eventId, jobId);
              }
              if (worker != null) {
                worker.wake();
              }
            }
          });
      return;
    }
    recordEventStage("no_transaction_synchronization", eventId, jobId);
    if (worker != null) {
      worker.wake();
    }
  }
}
