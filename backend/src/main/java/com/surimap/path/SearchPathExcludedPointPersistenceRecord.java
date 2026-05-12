package com.surimap.path;

import java.time.Instant;
import java.util.UUID;

public record SearchPathExcludedPointPersistenceRecord(
    UUID id,
    UUID searchPathId,
    String pointId,
    String reason,
    Instant clientTs,
    Instant createdAt,
    Instant updatedAt) {}
