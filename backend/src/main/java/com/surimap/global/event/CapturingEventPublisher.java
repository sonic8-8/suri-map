package com.surimap.global.event;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * EventPublisher 모의체 (Mock EventPublisher).
 *
 * <p>실제 event_dispatch_job 저장이나 SSE dispatch 없이 메모리에 발행 기록을 남긴다. 테스트에서 어떤 eventId로, 어떤 type으로, 어떤
 * incidentId에 대해 발행했는지 검증할 수 있다.
 *
 * <p>failure injection을 지원하여 특정 eventId에 대한 publish 호출을 RuntimeException으로 차단할 수 있다.
 *
 * <p>Spring 컨텍스트 없이 {@code new CapturingEventPublisher()}로 직접 생성하여 사용한다.
 */
public class CapturingEventPublisher implements EventPublisher {

  private final List<EventPublishRequest> publishes = new CopyOnWriteArrayList<>();
  private final Set<UUID> failureInjections = new HashSet<>();

  @Override
  public void publish(EventPublishRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    EventPublishRequestValidator.validate(request);

    if (failureInjections.contains(request.getEventId())) {
      throw new RuntimeException("publish_blocked_for_test");
    }

    publishes.add(request);
  }

  public Optional<EventPublishRequest> findByEventId(UUID eventId) {
    return publishes.stream().filter(p -> p.getEventId().equals(eventId)).findFirst();
  }

  public List<EventPublishRequest> findByType(String type) {
    return publishes.stream().filter(p -> type.equals(p.getType())).toList();
  }

  public List<EventPublishRequest> findByIncidentId(UUID incidentId) {
    return publishes.stream().filter(p -> p.getIncidentId().equals(incidentId)).toList();
  }

  public int getPublishCount() {
    return publishes.size();
  }

  public boolean hasNoPublishFor(UUID eventId) {
    return publishes.stream().noneMatch(p -> p.getEventId().equals(eventId));
  }

  public void injectFailureFor(UUID eventId) {
    failureInjections.add(eventId);
  }

  public void clearFailureInjection(UUID eventId) {
    failureInjections.remove(eventId);
  }

  public void reset() {
    publishes.clear();
    failureInjections.clear();
  }
}
