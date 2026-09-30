package com.surimap.eventhub.harness;

import com.surimap.global.event.CapturingEventPublisher;
import com.surimap.global.event.EventPublishRequest;
import java.util.Optional;
import java.util.UUID;

/**
 * CapturingEventPublisher 기반 EventHubContract stub 구현.
 *
 * <p>coder가 GREEN 구현을 완성하기 전까지 UnsupportedOperationException을 throw하여 RED 상태를 유지한다.
 */
public final class MockEventHubContract implements EventHubContract {

  private final CapturingEventPublisher delegate = new CapturingEventPublisher();

  @Override
  public EventHubHarnessFixtures.PublishEvidence publish(
      EventHubHarnessFixtures.HarnessRequest request) {
    delegate.publish(request.toPublishRequest());
    EventPublishRequest cp = delegate.findByEventId(request.eventId()).orElseThrow();
    return new EventHubHarnessFixtures.PublishEvidence(
        cp.getEventId(), cp.getIncidentId(), cp.getType(), cp.getPayload());
  }

  @Override
  public Optional<EventHubHarnessFixtures.SseReplayEvidence> replayEvidence(UUID eventId) {
    return Optional.empty();
  }

  @Override
  public int publishCount() {
    return delegate.getPublishCount();
  }

  @Override
  public int sseReplayCountForIncident(UUID incidentId) {
    return 0; // mock은 SSE replay store 없음
  }

  @Override
  public void reset() {
    delegate.reset();
  }
}
