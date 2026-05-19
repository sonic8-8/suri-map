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

@DisplayName("DbEventHub search area fanout")
class DbEventHubFanoutTest {

  @Test
  @DisplayName("SEARCH_AREA_CHANGED is persisted and synchronously delivered to offline package consumer")
  void searchAreaChangedIsPersistedAndDeliveredToConsumer() {
    CapturingEventDispatchJobMapper mapper = new CapturingEventDispatchJobMapper();
    CapturingSearchAreaChangedConsumer consumer = new CapturingSearchAreaChangedConsumer();
    DbEventHub eventHub = new DbEventHub(mapper, () -> List.of(consumer));
    PublishRequest event = searchAreaChangedEvent();

    eventHub.publish(event);

    assertThat(mapper.rows).hasSize(1);
    assertThat(mapper.rows.get(0).eventId()).isEqualTo(event.eventId());
    assertThat(consumer.events).containsExactly(event);
  }

  @Test
  @DisplayName("non search area events are only persisted")
  void nonSearchAreaEventsAreOnlyPersisted() {
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

    eventHub.publish(event);

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
    private final List<EventDispatchJobRow> rows = new ArrayList<>();

    @Override
    public void insert(EventDispatchJobRow row) {
      rows.add(row);
    }

    @Override
    public EventDispatchJobDispatchRecord claimById(UUID id, String claimStatus) {
      return null;
    }

    @Override
    public List<EventDispatchJobDispatchRecord> claimPending(int limit, String claimStatus) {
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
