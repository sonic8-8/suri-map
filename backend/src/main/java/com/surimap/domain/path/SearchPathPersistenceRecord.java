package com.surimap.domain.path;

import java.time.Instant;
import java.util.UUID;
import org.locationtech.jts.geom.Geometry;

public record SearchPathPersistenceRecord(
    UUID id,
    UUID dutyShiftId,
    UUID accountId,
    String status,
    Instant startedAt,
    Instant endedAt,
    Geometry geometry,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
