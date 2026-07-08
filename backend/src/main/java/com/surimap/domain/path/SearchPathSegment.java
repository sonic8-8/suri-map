package com.surimap.domain.path;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SearchPathSegment(
    String id,
    long version,
    MovementType movementType,
    MovementTypeSource movementTypeSource,
    int startIndex,
    int endIndex,
    String startPointId,
    String endPointId,
    UUID correctedByAccountId,
    OffsetDateTime correctedAt) {}
