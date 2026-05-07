package com.surimap.operationalperiod.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * OperationalPeriodQuery 소스 계약 포트 (S8.json §service_contracts).
 *
 * <p>current는 사건의 ACTIVE OP 단건을 반환한다. list는 사건 내 OP를 sequenceNumber ASC로 반환한다.
 */
public interface OperationalPeriodQuery {

  Optional<OperationalPeriodRow> current(UUID incidentId);

  List<OperationalPeriodRow> list(UUID incidentId);
}
