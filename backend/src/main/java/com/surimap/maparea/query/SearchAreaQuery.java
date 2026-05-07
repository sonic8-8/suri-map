package com.surimap.maparea.query;

import java.util.Optional;
import java.util.UUID;

/**
 * SearchAreaQuery 소스 계약 포트 (S2.json §service_contracts).
 *
 * <p>overallOf는 ACTIVE overall_search_area 단건을 반환한다. not_found이면 empty. byIncident/byOp는 area 컬렉션을
 * 반환한다. 구현체는 S2 DB 어댑터가 담당한다.
 */
public interface SearchAreaQuery {

  /**
   * incidentId 기준 ACTIVE overall_search_area를 조회한다.
   *
   * @param incidentId 사건 ID
   * @return ACTIVE overall_search_area 조회 결과, 없으면 empty
   */
  Optional<OverallSearchAreaResult> overallOf(UUID incidentId);

  /**
   * incidentId 기준 search_area 목록을 조회한다.
   *
   * @param incidentId 사건 ID
   * @param filters 조회 필터
   * @return area 컬렉션
   */
  SearchAreaCollection byIncident(UUID incidentId, SearchAreaFilters filters);

  /**
   * opId 기준 search_area 목록을 조회한다.
   *
   * @param opId OP ID
   * @param filters 조회 필터 (opId 필드는 무시됨)
   * @return area 컬렉션
   */
  SearchAreaCollection byOp(UUID opId, SearchAreaFilters filters);
}
