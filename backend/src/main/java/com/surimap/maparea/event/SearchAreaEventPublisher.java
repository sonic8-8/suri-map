package com.surimap.maparea.event;

import java.util.Set;

/**
 * S4 EventHub.publish 포트 인터페이스.
 *
 * <p>테스트에서는 PublishRequestCollector가 이 인터페이스를 구현하여 in-memory stub으로 동작한다. 기준 문서:
 * docs/spec/specs/S2.json §dependencies S4 stub_strategy.
 */
public interface SearchAreaEventPublisher {

  /**
   * 이벤트를 발행한다.
   *
   * @param eventType 이벤트 타입 문자열 (예: "SEARCH_AREA_CHANGED")
   * @param payloadFieldNames payload에 포함된 field name 목록
   */
  void publish(String eventType, Set<String> payloadFieldNames);

  /**
   * domain write 시 영향 받은 테이블 이름을 기록한다.
   *
   * @param tableName 영향 받은 테이블 이름
   */
  void recordMutatedTable(String tableName);
}
