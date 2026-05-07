package com.surimap.maparea.query;

import java.util.List;
import java.util.UUID;

/**
 * SearchAreaAssignmentQuery 소스 계약 포트 (S2.json §service_contracts).
 *
 * <p>byOp는 opId 기준으로 해당 OP의 모든 ACTIVE search_area_assignment를 반환한다.
 * byArea는 searchAreaId 기준으로 배정 이력을 반환한다.
 * 구현체는 S2 DB 어댑터가 담당한다.
 */
public interface SearchAreaAssignmentQuery {

  /**
   * opId 기준 ACTIVE search_area_assignment 목록을 반환한다.
   *
   * @param opId OP ID
   * @return 해당 OP에 속한 ACTIVE assignment 컬렉션
   */
  List<SearchAreaAssignmentRow> byOp(UUID opId);

  /**
   * searchAreaId 기준 search_area_assignment 목록을 반환한다 (이력 포함).
   *
   * @param searchAreaId 수색 구역 ID
   * @return 해당 구역의 assignment 이력 컬렉션
   */
  List<SearchAreaAssignmentRow> byArea(UUID searchAreaId);
}
