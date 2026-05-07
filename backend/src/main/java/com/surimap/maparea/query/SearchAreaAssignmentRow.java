package com.surimap.maparea.query;

import java.time.Instant;
import java.util.UUID;

/**
 * SearchAreaAssignmentQuery.byOp / byArea 응답 row (S2.json entity 기준).
 *
 * <p>status는 ACTIVE 또는 CANCELLED. revokedAt은 CANCELLED일 때만 non-null.
 */
public record SearchAreaAssignmentRow(
    UUID id,
    UUID searchAreaId,
    UUID assignedAccountId,
    UUID assignedByAccountId,
    Instant assignedAt,
    Instant revokedAt,
    String status,
    long version) {}
