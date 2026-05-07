package com.surimap.maparea.event;

import java.util.ArrayList;
import java.util.List;

/**
 * SearchAreaEventPublisher 테스트 더블.
 *
 * <p>publish()로 수신한 PublishRequest를 메모리에 수집한다. 테스트에서 발행된 이벤트를 검증하는 데 사용한다.
 */
public class PublishRequestCollector implements SearchAreaEventPublisher {

  private final List<PublishRequest> collected = new ArrayList<>();

  @Override
  public void publish(PublishRequest request) {
    collected.add(request);
  }

  /** 수집된 모든 이벤트 타입 목록을 반환한다. */
  public List<String> publishedTypes() {
    return collected.stream().map(PublishRequest::eventType).toList();
  }

  /** 지정한 이벤트 타입으로 발행된 PublishRequest 목록을 반환한다. */
  public List<PublishRequest> publishedByType(String eventType) {
    return collected.stream().filter(r -> eventType.equals(r.eventType())).toList();
  }

  /** 수집된 이벤트를 모두 지운다. */
  public void clear() {
    collected.clear();
  }

  /** 수집된 이벤트가 없으면 true를 반환한다. */
  public boolean isEmpty() {
    return collected.isEmpty();
  }
}
