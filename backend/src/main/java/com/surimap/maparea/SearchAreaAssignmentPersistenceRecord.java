package com.surimap.maparea;

import java.time.Instant;
import java.util.UUID;

public record SearchAreaAssignmentPersistenceRecord(
    UUID id,
    UUID searchAreaId,
    UUID assignedAccountId,
    UUID assignedByAccountId,
    Instant assignedAt,
    Instant revokedAt,
    String status,
    String memo,
    Instant createdAt,
    Instant updatedAt) {}
