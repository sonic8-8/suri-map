package com.surimap.eventhub.harness;

import com.surimap.eventhub.stream.InMemorySseReplayEventStore;
import com.surimap.global.event.CapturingEventPublisher;
import com.surimap.global.event.EventPublishRequest;
import java.util.Optional;
import java.util.UUID;

/**
 * CapturingEventPublisher + InMemorySseReplayEventStore 기반 EventHubContract stub 구현.
 *
 * <p>publish() 시 CapturingEventPublisher.publish() 와 replayStore.append() 를 모두 호출하는 완전한 in-memory
 * S4 구현이다. coder가 GREEN 구현을 완성하기 전까지 UnsupportedOperationException을 throw하여 RED 상태를 유지한다.
 */
public final class InMemoryS4EventHubContract implements EventHubContract {

  private final CapturingEventPublisher mockEventHub = new CapturingEventPublisher();
  private final InMemorySseReplayEventStore replayStore = new InMemorySseReplayEventStore();

  @Override
  public EventHubHarnessFixtures.PublishEvidence publish(
      EventHubHarnessFixtures.HarnessRequest request) {
    mockEventHub.publish(request.toPublishRequest());
    replayStore.append(request.eventId(), request.toPublishRequest());
    EventPublishRequest cp = mockEventHub.findByEventId(request.eventId()).orElseThrow();
    return new EventHubHarnessFixtures.PublishEvidence(
        cp.getEventId(), cp.getIncidentId(), cp.getType(), cp.getPayload());
  }

  @Override
  public Optional<EventHubHarnessFixtures.SseReplayEvidence> replayEvidence(UUID eventId) {
    return replayStore
        .findByEventId(eventId)
        .map(
            ra ->
                new EventHubHarnessFixtures.SseReplayEvidence(
                    ra.eventId(), ra.incidentId(), ra.replaySequence()));
  }

  @Override
  public int publishCount() {
    return mockEventHub.getPublishCount();
  }

  @Override
  public int sseReplayCountForIncident(UUID incidentId) {
    return replayStore.findByIncidentId(incidentId).size();
  }

  @Override
  public void reset() {
    mockEventHub.reset();
    replayStore.clear();
  }
}
