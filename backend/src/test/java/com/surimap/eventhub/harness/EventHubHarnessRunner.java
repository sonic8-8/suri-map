package com.surimap.eventhub.harness;

import java.util.Optional;

/**
 * L2-T09B EventHub 하네스 러너.
 *
 * <p>다른 Lane 테스트에서 S4 EventHub 하네스 픽스처를 실행하는 데 사용한다.
 */
public final class EventHubHarnessRunner {

  private final EventHubContract contract;
  private final EventHubHarnessFixtures.PublishEvidence publishEvidence;
  private final Optional<EventHubHarnessFixtures.SseReplayEvidence> replayEvidence;

  private EventHubHarnessRunner(
      EventHubContract contract, EventHubHarnessFixtures.HarnessRequest request) {
    this.contract = contract;
    this.publishEvidence = contract.publish(request);
    this.replayEvidence = contract.replayEvidence(request.eventId());
  }

  /** MockEventHubContract 로 픽스처를 실행한다. */
  public static EventHubHarnessRunner mock(EventHubHarnessFixtures.HarnessRequest request) {
    return new EventHubHarnessRunner(new MockEventHubContract(), request);
  }

  /** InMemoryS4EventHubContract 로 픽스처를 실행한다. */
  public static EventHubHarnessRunner inMemoryS4(EventHubHarnessFixtures.HarnessRequest request) {
    return new EventHubHarnessRunner(new InMemoryS4EventHubContract(), request);
  }

  /** 주어진 contract 로 픽스처를 실행한다. */
  public static EventHubHarnessRunner run(
      EventHubContract contract, EventHubHarnessFixtures.HarnessRequest request) {
    return new EventHubHarnessRunner(contract, request);
  }

  public EventHubHarnessFixtures.PublishEvidence publishEvidence() {
    return publishEvidence;
  }

  public Optional<EventHubHarnessFixtures.SseReplayEvidence> replayEvidence() {
    return replayEvidence;
  }
}
