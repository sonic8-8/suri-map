package com.surimap.path;

import java.time.Instant;
import java.util.UUID;
import org.locationtech.jts.geom.Geometry;

public record SearchPathSegmentReadRecord(
    UUID id,
    UUID searchPathId,
    String movementType,
    String movementTypeSource,
    Geometry geometry,
    Instant startedAt,
    Instant endedAt,
    UUID correctedByAccountId,
    Instant correctedAt,
    long version) {}
