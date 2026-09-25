package com.surimap.eventhub.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.eventhub.consumer.DomainEventConsumer;
import com.surimap.eventhub.dto.PublishRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DbEventHubFanoutTest {

  @Test
  @DisplayName("수색 구역 변경 이벤트를 저장하면 해당 소비자에게 동기 전달한다")
  void search_area_changed_is_persisted_and_delivered_to_consumer() {
    // given: 수색 구역 변경을 처리하는 소비자가 등록돼 있다.
    CapturingEventDispatchJobMapper mapper = new CapturingEventDispatchJobMapper();
    CapturingSearchAreaChangedConsumer consumer = new CapturingSearchAreaChangedConsumer();
    DbEventHub eventHub = new DbEventHub(mapper, () -> List.of(consumer));
    PublishRequest event = searchAreaChangedEvent();

    // when: 수색 구역 변경 이벤트를 발행한다.
    eventHub.publish(event);

    // then: 전송 작업으로 저장하고 소비자에게 같은 이벤트를 전달한다.
    assertThat(mapper.rows).hasSize(1);
    assertThat(mapper.rows.get(0).getEventId()).isEqualTo(event.eventId());
    assertThat(consumer.events).containsExactly(event);
  }

  @Test
  @DisplayName("수색 구역 변경이 아닌 이벤트는 저장하되 해당 소비자에게 전달하지 않는다")
  void other_event_is_persisted_without_delivery_to_search_area_consumer() {
    // given: 수색 구역 소비자와 다른 유형의 이벤트를 준비한다.
    CapturingEventDispatchJobMapper mapper = new CapturingEventDispatchJobMapper();
    CapturingSearchAreaChangedConsumer consumer = new CapturingSearchAreaChangedConsumer();
    DbEventHub eventHub = new DbEventHub(mapper, () -> List.of(consumer));
    PublishRequest event =
        new PublishRequest(
            UUID.fromString("20000000-0000-4000-8000-000000000001"),
            UUID.fromString("10000000-0000-4000-8000-000000000001"),
            "OFFLINE_PACKAGE_INSTALLATION_CHANGED",
            1,
            "offline_package_installation",
            UUID.fromString("30000000-0000-4000-8000-000000000001"),
            Instant.parse("2026-05-19T00:00:00Z"),
            Map.of("id", "pkg-1"));

    // when: 다른 유형의 이벤트를 발행한다.
    eventHub.publish(event);

    // then: 저장은 유지하되 처리 대상이 아닌 소비자에는 전달하지 않는다.
    assertThat(mapper.rows).hasSize(1);
    assertThat(consumer.events).isEmpty();
  }

  private static PublishRequest searchAreaChangedEvent() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", "30000000-0000-4000-8000-000000000001");
    payload.put("incidentId", "10000000-0000-4000-8000-000000000001");
    payload.put("version", 2L);
    return new PublishRequest(
        UUID.fromString("20000000-0000-4000-8000-000000000001"),
        UUID.fromString("10000000-0000-4000-8000-000000000001"),
        "SEARCH_AREA_CHANGED",
        1,
        "search_area",
        UUID.fromString("30000000-0000-4000-8000-000000000001"),
        Instant.parse("2026-05-19T00:00:00Z"),
        payload);
  }

  private static final class CapturingEventDispatchJobMapper implements EventDispatchJobMapper {
    private final List<EventDispatchJob> rows = new ArrayList<>();

    @Override
    public void insert(EventDispatchJob row) {
      rows.add(row);
    }

    @Override
    public EventDispatchJob findById(UUID id) {
      return null;
    }

    @Override
    public EventDispatchJob findByIdForUpdate(UUID id) {
      return null;
    }

    @Override
    public List<EventDispatchJob> findBySseSequenceRange(
        UUID incidentId, long afterSequence, long throughSequence, int limit) {
      return List.of();
    }

    @Override
    public int assignSseSequenceIfAbsent(UUID id, long sseSequence) {
      return 0;
    }

    @Override
    public EventDispatchJob claimById(UUID id, String claimStatus) {
      return null;
    }

    @Override
    public List<EventDispatchJob> claimPending(int limit, String claimStatus) {
      return List.of();
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
    private final List<PublishRequest> events = new ArrayList<>();

    @Override
    public boolean supports(PublishRequest event) {
      return event != null && "SEARCH_AREA_CHANGED".equals(event.type());
    }

    @Override
    public void consume(PublishRequest event) {
      events.add(event);
    }
  }
}
