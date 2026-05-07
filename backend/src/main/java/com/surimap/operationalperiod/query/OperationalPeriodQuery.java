package com.surimap.operationalperiod.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * OperationalPeriodQuery source contract 포트 (S8.json §service_contracts).
 *
 * <p>current OP와 OP 목록 조회를 제공한다. 구현체는 S8 DB 어댑터가 담당한다.
 */
public interface OperationalPeriodQuery {

  /**
   * incidentId 기준으로 현재 ACTIVE 상태인 OP를 조회한다. OP1 bootstrap 이전이면 empty를 반환한다.
   *
   * @param incidentId 사건 ID
   * @return current ACTIVE OP 조회 결과, 없으면 empty
   */
  Optional<CurrentOpResult> current(UUID incidentId);

  // TODO 쿼리 구현 예정. 문서에는 없으나 L5에서 구현 요청한 항목
  // Optional<OperationalPeriodRow> findById(UUID opId);

  /**
   * incidentId 기준으로 OP 목록을 sequenceNo 오름차순으로 조회한다.
   *
   * @param incidentId 사건 ID
   * @return OP 목록
   */
  List<OperationalPeriodRow> list(UUID incidentId);
}
