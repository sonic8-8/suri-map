package com.surimap.operationalperiod.query;

import java.time.Instant;
import java.util.UUID;

/**
 * OperationalPeriodQuery.current 응답 shape (S8.json §service_contracts).
 */
public record CurrentOpResult(
        UUID opId,
        UUID incidentId,
        String status,
        int sequenceNo,
        Instant startedAt,
        Instant endedAt,
        String reason,
        long version
) {}
