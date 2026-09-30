package com.surimap.global.event;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.surimap.global.sse.ServerSentEventJob;
import com.surimap.global.sse.ServerSentEventJobMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DatabaseEventPublisherTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @DisplayName("커밋 콜백이 실행된 경우에만 커밋 후 관측 로그를 남긴다")
  void event_measurement_records_commit_only_when_commit_callback_runs(boolean committed) {
    // given: 계측 로그를 켜고 트랜잭션 콜백을 등록할 실행 문맥을 준비한다.
    Logger logger = (Logger) LoggerFactory.getLogger(DatabaseEventPublisher.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> records = new ListAppender<>();
    records.start();
    logger.addAppender(records);
    logger.setLevel(Level.DEBUG);
    TransactionSynchronizationManager.setActualTransactionActive(true);
    TransactionSynchronizationManager.initSynchronization();
    EventPublishRequest event = searchAreaChangedEvent();
    try {
      DatabaseEventPublisher publisher =
          new DatabaseEventPublisher(new CapturingEventDispatchJobMapper());
      publisher.publish(event);
      assertThat(records.list).hasSize(1);
      assertThat(records.list.get(0).getFormattedMessage())
          .contains("stage=outbox_insert_returned", "eventId=" + event.getEventId())
          .doesNotContain("payload=");

      // when: 커밋 또는 롤백에 해당하는 콜백만 호출한다. 실제 DB 검증은 별도 통합 테스트다.
      for (TransactionSynchronization synchronization :
          TransactionSynchronizationManager.getSynchronizations()) {
        if (committed) {
          synchronization.afterCommit();
        } else {
          synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
      }

      // then: 롤백에는 커밋 로그가 없고 커밋에는 이벤트 ID와 두 종류의 시각이 남는다.
      assertThat(records.list).hasSize(committed ? 2 : 1);
      if (committed) {
        assertThat(records.list.get(1).getFormattedMessage())
            .contains("stage=transaction_after_commit", "eventId=" + event.getEventId())
            .matches(".*wallTimeMs=\\d+ monotonicNs=-?\\d+");
      }
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(false);
      logger.detachAppender(records);
      logger.setLevel(previousLevel);
      records.stop();
    }
  }

  @Test
  @DisplayName("수색 구역 변경 이벤트를 저장하면 해당 소비자에게 동기 전달한다")
  void search_area_changed_is_persisted_and_delivered_to_consumer() {
    // given: 수색 구역 변경을 처리하는 소비자가 등록돼 있다.
    CapturingEventDispatchJobMapper mapper = new CapturingEventDispatchJobMapper();
    CapturingSearchAreaChangedConsumer consumer = new CapturingSearchAreaChangedConsumer();
    DatabaseEventPublisher eventHub = new DatabaseEventPublisher(mapper, () -> List.of(consumer));
    EventPublishRequest event = searchAreaChangedEvent();

    // when: 수색 구역 변경 이벤트를 발행한다.
    eventHub.publish(event);

    // then: 전송 작업으로 저장하고 소비자에게 같은 이벤트를 전달한다.
    assertThat(mapper.rows).hasSize(1);
    assertThat(mapper.rows.get(0).getEventId()).isEqualTo(event.getEventId());
    assertThat(consumer.events).containsExactly(event);
  }

  @Test
  @DisplayName("수색 구역 변경이 아닌 이벤트는 저장하되 해당 소비자에게 전달하지 않는다")
  void other_event_is_persisted_without_delivery_to_search_area_consumer() {
    // given: 수색 구역 소비자와 다른 유형의 이벤트를 준비한다.
    CapturingEventDispatchJobMapper mapper = new CapturingEventDispatchJobMapper();
    CapturingSearchAreaChangedConsumer consumer = new CapturingSearchAreaChangedConsumer();
    DatabaseEventPublisher eventHub = new DatabaseEventPublisher(mapper, () -> List.of(consumer));
    EventPublishRequest event =
        EventPublishRequest.builder()
            .eventId(UUID.fromString("20000000-0000-4000-8000-000000000001"))
            .incidentId(UUID.fromString("10000000-0000-4000-8000-000000000001"))
            .type("OFFLINE_PACKAGE_INSTALLATION_CHANGED")
            .payloadFormatVersion(1)
            .sourceEntityType("offline_package_installation")
            .sourceEntityId(UUID.fromString("30000000-0000-4000-8000-000000000001"))
            .occurredAt(Instant.parse("2026-05-19T00:00:00Z"))
            .payload(Map.of("id", "pkg-1"))
            .build();

    // when: 다른 유형의 이벤트를 발행한다.
    eventHub.publish(event);

    // then: 저장은 유지하되 처리 대상이 아닌 소비자에는 전달하지 않는다.
    assertThat(mapper.rows).hasSize(1);
    assertThat(consumer.events).isEmpty();
  }

  private static EventPublishRequest searchAreaChangedEvent() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", "30000000-0000-4000-8000-000000000001");
    payload.put("incidentId", "10000000-0000-4000-8000-000000000001");
    payload.put("version", 2L);
    return EventPublishRequest.builder()
        .eventId(UUID.fromString("20000000-0000-4000-8000-000000000001"))
        .incidentId(UUID.fromString("10000000-0000-4000-8000-000000000001"))
        .type("SEARCH_AREA_CHANGED")
        .payloadFormatVersion(1)
        .sourceEntityType("search_area")
        .sourceEntityId(UUID.fromString("30000000-0000-4000-8000-000000000001"))
        .occurredAt(Instant.parse("2026-05-19T00:00:00Z"))
        .payload(payload)
        .build();
  }

  private static final class CapturingEventDispatchJobMapper implements ServerSentEventJobMapper {
    private final List<ServerSentEventJob> rows = new ArrayList<>();

    @Override
    public void insert(ServerSentEventJob row) {
      rows.add(row);
    }

    @Override
    public ServerSentEventJob findById(UUID id) {
      return null;
    }

    @Override
    public ServerSentEventJob findByIdForUpdate(UUID id) {
      return null;
    }

    @Override
    public boolean isIncidentOpenAndNotPurged(UUID incidentId) {
      throw new UnsupportedOperationException("fanout test does not validate SSE writes");
    }

    @Override
    public List<ServerSentEventJob> findBySseSequenceRange(
        UUID incidentId, long afterSequence, long throughSequence, int limit) {
      return List.of();
    }

    @Override
    public int assignSseSequenceIfAbsent(UUID id, long sseSequence) {
      return 0;
    }

    @Override
    public long countBySseSequenceRange(UUID incidentId, long afterSequence, long throughSequence) {
      throw new UnsupportedOperationException("fanout test does not read replay history");
    }

    @Override
    public ServerSentEventJob findLatestSequencedIncidentClosedEvent(UUID incidentId) {
      throw new UnsupportedOperationException("fanout test does not read terminal history");
    }

    @Override
    public ServerSentEventJob claimById(UUID id, String claimStatus) {
      return null;
    }

    @Override
    public List<ServerSentEventJob> claimPending(int limit, String claimStatus) {
      return List.of();
    }

    @Override
    public int requeueInterruptedJobs() {
      return 0;
    }

    @Override
    public int requeueFailedJobs() {
      return 0;
    }

    @Override
    public int markCompleted(UUID id, String completedStatus) {
      return 0;
    }

    @Override
    public int markFailed(UUID id, String failedStatus) {
      return 0;
    }
  }

  private static final class CapturingSearchAreaChangedConsumer implements DomainEventConsumer {
    private final List<EventPublishRequest> events = new ArrayList<>();

    @Override
    public boolean supports(EventPublishRequest event) {
      return event != null && "SEARCH_AREA_CHANGED".equals(event.getType());
    }

    @Override
    public void consume(EventPublishRequest event) {
      events.add(event);
    }
  }
}
