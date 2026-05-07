package com.surimap.maparea.event;

/**
 * S2 수색 구역 이벤트 발행 포트.
 *
 * <p>S2는 SEARCH_AREA_CHANGED PublishRequest 생성·검증과 EventHub.publish 요청을 이 포트를 통해 수행한다.
 *
 * <p>실제 event_dispatch_job row와 fanout orchestration은 S4 소유다.
 *
 * <p>기준 문서: docs/spec/specs/S2.json events_published, interface_contracts.
 */
public interface SearchAreaEventPublisher {

  /**
   * 수색 구역 변경 이벤트를 발행한다.
   *
   * <p>SEARCH_AREA_CHANGED 타입으로 발행하며, mutated_tables에 search_area가 포함된다.
   *
   * @param request 발행할 이벤트 요청 정보
   */
  void publish(PublishRequest request);

  /**
   * S2 이벤트 발행 요청.
   *
   * @param eventType 이벤트 타입 (예: SEARCH_AREA_CHANGED)
   * @param mutatedTable 변경된 테이블 이름
   * @param payload 이벤트 payload
   */
  record PublishRequest(String eventType, String mutatedTable, Object payload) {}
}
