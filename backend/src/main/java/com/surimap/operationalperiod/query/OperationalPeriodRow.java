package com.surimap.operationalperiod.query;

import java.time.Instant;
import java.util.UUID;

/** OperationalPeriodQuery.list 응답 row shape (S8.json §service_contracts). */
public record OperationalPeriodRow(
    UUID opId,
    UUID incidentId,
    String status,
    int sequenceNo, // 수색차수 번호
    Instant startedAt,
    Instant endedAt,
    String reason,
    long version) {}
