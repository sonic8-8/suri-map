package com.surimap.marker.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * S8 current OP 조회 port. S5 marker write가 OP 귀속 검증을 위해 소비한다.
 *
 * @see docs/spec/specs/S5.json
 * @see docs/spec/boundaries.md
 */
public interface OperationalPeriodQueryPort {

  /**
   * 사건의 현재 ACTIVE OP ID를 반환한다.
   *
   * @param incidentId 사건 ID
   * @return 현재 active OP ID, 없으면 empty
   */
  Optional<UUID> findCurrentOpId(UUID incidentId);
}
