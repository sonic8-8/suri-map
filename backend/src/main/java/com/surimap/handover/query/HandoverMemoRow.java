package com.surimap.handover.query;

import java.time.Instant;
import java.util.UUID;

/**
 * HandoverMemoQuery.byContext 응답 row (S8.json §service_contracts).
 *
 * <p>rowShape: memoId, incidentId, opId, dutyShiftId, targetType, targetId, content,
 * createdByAccountId, createdAt, version
 */
public record HandoverMemoRow(
    UUID memoId,
    UUID incidentId,
    UUID opId,
    UUID dutyShiftId,
    String targetType,
    UUID targetId,
    String content,
    UUID createdByAccountId,
    Instant createdAt,
    long version) {}
