package com.surimap.maparea.event;

import com.surimap.maparea.event.SearchAreaEventPublisher.StateTransitionPublishRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SearchAreaEventPublisher 테스트 더블.
 *
 * <p>실제 EventHub.publish 없이 발행된 이벤트를 메모리에 수집해 테스트에서 검증한다.
 *
 * <p>기준 문서: docs/spec/specs/S2.json events_published.
 */
public class PublishRequestCollector implements SearchAreaEventPublisher {

  private final List<SearchAreaEventPublisher.PublishRequest> collected = new ArrayList<>();
  private final List<StateTransitionPublishRequest> stateTransitions = new ArrayList<>();

  @Override
  public void publish(SearchAreaEventPublisher.PublishRequest request) {
    collected.add(request);
  }

  @Override
  public void publish(StateTransitionPublishRequest request) {
    stateTransitions.add(request);
    SearchAreaEventPublisher.super.publish(request);
  }

  /**
   * 상태 전이 이벤트 발행 요청 목록을 반환한다.
   *
   * @return 상태 전이 발행 요청 목록
   */
  public List<StateTransitionPublishRequest> collected() {
    return Collections.unmodifiableList(stateTransitions);
  }

  /**
   * 발행된 이벤트 타입 목록을 반환한다.
   *
   * @return 발행된 eventType 값 목록 (순서 보장)
   */
  public List<String> publishedTypes() {
    return collected.stream()
        .map(SearchAreaEventPublisher.PublishRequest::eventType)
        .collect(Collectors.toList());
  }

  /**
   * 지정한 이벤트 타입으로 발행된 PublishRequest 목록을 반환한다.
   *
   * @param eventType 조회할 이벤트 타입
   * @return eventType이 일치하는 발행 요청 목록
   */
  public List<SearchAreaEventPublisher.PublishRequest> publishedByType(String eventType) {
    return collected.stream()
        .filter(r -> eventType.equals(r.eventType()))
        .collect(Collectors.toList());
  }

  /**
   * 발행 요청에 포함된 변경 테이블 목록을 반환한다.
   *
   * @return 발행된 mutatedTable 값 목록 (순서 보장)
   */
  public List<String> mutatedTables() {
    return collected.stream()
        .map(SearchAreaEventPublisher.PublishRequest::mutatedTable)
        .collect(Collectors.toList());
  }

  /**
   * 발행된 payload의 필드명 집합을 반환한다.
   *
   * <p>payload가 record 또는 Map이면 field 이름 또는 key 집합을 반환한다.
   *
   * @return payload field name 집합
   */
  public Set<String> publishedPayloadFieldNames() {
    return collected.stream()
        .flatMap(
            r -> {
              Object payload = r.payload();
              if (payload == null) {
                return java.util.stream.Stream.empty();
              }
              if (payload instanceof java.util.Map<?, ?> map) {
                return map.keySet().stream().map(Object::toString);
              }
              java.lang.reflect.RecordComponent[] components =
                  payload.getClass().getRecordComponents();
              if (components == null) {
                return java.util.stream.Stream.empty();
              }
              return java.util.Arrays.stream(components)
                  .map(java.lang.reflect.RecordComponent::getName);
            })
        .collect(Collectors.toSet());
  }

  /** 수집된 데이터를 초기화한다. */
  public void clear() {
    collected.clear();
    stateTransitions.clear();
  }

  /**
   * 수집된 이벤트가 없는지 확인한다.
   *
   * @return 수집된 이벤트가 없으면 true
   */
  public boolean isEmpty() {
    return collected.isEmpty();
  }
}
