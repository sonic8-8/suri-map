package com.surimap.path;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PathSegmentCorrectionResponse(
    String id,
    MovementType movementType,
    MovementTypeSource movementTypeSource,
    UUID opId,
    UUID policePhoneId,
    UUID correctedByAccountId,
    OffsetDateTime correctedAt,
    long version) {}
