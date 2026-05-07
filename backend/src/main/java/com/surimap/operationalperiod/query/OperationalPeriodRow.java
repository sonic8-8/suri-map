package com.surimap.operationalperiod.query;

import java.time.Instant;
import java.util.UUID;

/** OperationalPeriodQuery.current/list 응답 row (S8.json §service_contracts). */
public record OperationalPeriodRow(
    UUID opId,
    UUID incidentId,
    String status,
    int sequenceNumber,
    Instant startedAt,
    Instant endedAt,
    String reason,
    long version) {}
