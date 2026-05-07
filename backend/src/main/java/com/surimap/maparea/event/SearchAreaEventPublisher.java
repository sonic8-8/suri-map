package com.surimap.maparea.event;

/**
 * S2 search_area 도메인 이벤트 발행 포트.
 *
 * <p>split, 상태 변경 등 search_area 변형 이후 외부에 알릴 이벤트를 발행한다.
 */
public interface SearchAreaEventPublisher {

  /**
   * 이벤트를 발행한다.
   *
   * @param request 발행할 이벤트 요청
   */
  void publish(PublishRequest request);

  /**
   * 발행할 이벤트 요청.
   *
   * @param eventType 이벤트 타입 (예: SEARCH_AREA_CHANGED)
   * @param mutatedTable 변경된 테이블 이름
   * @param payload 이벤트 페이로드
   */
  record PublishRequest(String eventType, String mutatedTable, Object payload) {}
}
