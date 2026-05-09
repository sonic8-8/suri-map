package com.surimap.eventhub.harness;

import java.util.Optional;
import java.util.UUID;

/**
 * L2-T09B EventHub 하네스 계약 인터페이스.
 *
 * <p>mock 구현과 inMemoryS4 구현을 동일 경계로 교환할 수 있도록 정의하는 swap boundary.
 */
public interface EventHubContract {

  EventHubHarnessFixtures.PublishEvidence publish(EventHubHarnessFixtures.HarnessRequest request);

  Optional<EventHubHarnessFixtures.SseReplayEvidence> replayEvidence(UUID eventId);

  int publishCount();

  /** SSE replay store에 append된 사건별 row 수. mock 계약은 0 반환. */
  int sseReplayCountForIncident(UUID incidentId);

  void reset();
}
