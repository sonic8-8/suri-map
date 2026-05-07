package com.surimap.marker.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * S8 current OP 조회 port.
 *
 * <p>S5 marker write는 이 포트를 소비해 request.opId가 서버 current OP와 같은지 검증한다.
 */
public interface OperationalPeriodQueryPort {

  /**
   * 사건의 현재 OP ID를 반환한다.
   *
   * @param incidentId 사건 ID
   * @return current OP ID, 없으면 empty
   */
  Optional<UUID> findCurrentOpId(UUID incidentId);
}
