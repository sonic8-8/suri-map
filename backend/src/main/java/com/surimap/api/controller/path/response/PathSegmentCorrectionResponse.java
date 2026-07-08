package com.surimap.api.controller.path.response;

import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.MovementTypeSource;
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
