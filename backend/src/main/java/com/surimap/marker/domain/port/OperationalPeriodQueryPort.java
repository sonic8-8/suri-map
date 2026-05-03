package com.surimap.marker.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * S8 수색차수(OP) 조회 port.
 * L3(김희수)가 구현하고, L5가 소비한다.
 *
 * @see docs/contracts/L5-06-op-query-dto.md §2
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
