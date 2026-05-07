package com.surimap.maparea.event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * S4 EventHub.publish in-memory stub.
 *
 * <p>S2.json §dependencies S4 stub_strategy: "in-memory PublishRequest collector with payload
 * schema checks".
 *
 * <p>publish 호출 시 event type과 mutated table 목록, payload field name을 기록한다. RED 테스트는 이 기록을 검증한다.
 */
public final class PublishRequestCollector implements SearchAreaEventPublisher {

  private final List<String> publishedTypes = new ArrayList<>();
  private final Set<String> mutatedTables = new HashSet<>();
  private final Set<String> publishedPayloadFieldNames = new HashSet<>();

  /**
   * SEARCH_AREA_CHANGED 또는 SEARCH_AREA_ASSIGNMENT_CHANGED publish 요청을 기록한다.
   *
   * @param eventType 이벤트 타입 문자열 (예: "SEARCH_AREA_CHANGED")
   * @param payloadFieldNames 이벤트 payload에 포함된 field name 목록
   */
  public void publish(String eventType, Set<String> payloadFieldNames) {
    publishedTypes.add(eventType);
    publishedPayloadFieldNames.addAll(payloadFieldNames);
  }

  /**
   * domain write 시 영향 받은 테이블 이름을 기록한다.
   *
   * @param tableName 영향 받은 테이블 이름
   */
  public void recordMutatedTable(String tableName) {
    mutatedTables.add(tableName);
  }

  /** publish된 이벤트 타입 목록을 반환한다 (순서 보존). */
  public List<String> publishedTypes() {
    return List.copyOf(publishedTypes);
  }

  /** domain write가 발생한 테이블 이름 집합을 반환한다. */
  public Set<String> mutatedTables() {
    return Set.copyOf(mutatedTables);
  }

  /** publish된 모든 payload field name 집합을 반환한다. */
  public Set<String> publishedPayloadFieldNames() {
    return Set.copyOf(publishedPayloadFieldNames);
  }

  /** 수집된 상태를 초기화한다. */
  public void clear() {
    publishedTypes.clear();
    mutatedTables.clear();
    publishedPayloadFieldNames.clear();
  }
}
