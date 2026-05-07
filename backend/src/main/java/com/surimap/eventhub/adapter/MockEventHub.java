package com.surimap.eventhub.adapter;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * EventHub 모의체 (Mock EventHub).
 *
 * <p>실제 event_dispatch_job 저장이나 SSE dispatch 없이 메모리에 발행 기록을 남긴다. 테스트에서 어떤 eventId로, 어떤
 * type으로, 어떤 incidentId에 대해 발행했는지 검증할 수 있다.
 *
 * <p>failure injection을 지원하여 특정 eventId에 대한 publish 호출을 RuntimeException으로 차단할 수 있다.
 *
 * <p>Spring 컨텍스트 없이 {@code new MockEventHub()}로 직접 생성하여 사용한다.
 */
public class MockEventHub implements EventHub {

  private final List<CapturedPublish> publishes = new CopyOnWriteArrayList<>();
  private final Set<UUID> failureInjections = new HashSet<>();

  @Override
  public void publish(PublishRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    if (failureInjections.contains(request.eventId())) {
      throw new RuntimeException("publish_blocked_for_test");
    }

    publishes.add(
        new CapturedPublish(
            request.eventId(),
            request.incidentId(),
            request.type(),
            request.payloadFormatVersion(),
            request.sourceEntityType(),
            request.sourceEntityId(),
            request.occurredAt(),
            request.payload()));
  }

  public Optional<CapturedPublish> findByEventId(UUID eventId) {
    return publishes.stream().filter(p -> p.eventId().equals(eventId)).findFirst();
  }

  public List<CapturedPublish> findByType(String type) {
    return publishes.stream().filter(p -> type.equals(p.type())).toList();
  }

  public List<CapturedPublish> findByIncidentId(UUID incidentId) {
    return publishes.stream().filter(p -> p.incidentId().equals(incidentId)).toList();
  }

  public int getPublishCount() {
    return publishes.size();
  }

  public boolean hasNoPublishFor(UUID eventId) {
    return publishes.stream().noneMatch(p -> p.eventId().equals(eventId));
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

  /**
   * 하나의 이벤트 발행 기록.
   *
   * <p>boundaries.md §9.1 BaseEvent envelope의 모든 메타 필드를 캡처한다.
   */
  public record CapturedPublish(
      UUID eventId,
      UUID incidentId,
      String type,
      int payloadFormatVersion,
      String sourceEntityType,
      UUID sourceEntityId,
      Instant occurredAt,
      Map<String, Object> payload) {}
}
