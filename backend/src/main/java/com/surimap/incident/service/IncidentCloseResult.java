package com.surimap.incident.service;

import java.time.Instant;
import java.util.UUID;

/** 사건 종료 use case 결과 — close 응답과 terminalSnapshot이 같은 terminal 필드에 수렴한다. */
public record IncidentCloseResult(
    UUID id,
    UUID incidentId,
    String status,
    long version,
    Instant closedAt,
    String writeDisabledReason) {}
