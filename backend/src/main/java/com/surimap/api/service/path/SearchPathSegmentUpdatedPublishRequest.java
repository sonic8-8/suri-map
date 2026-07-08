package com.surimap.api.service.path;

import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.MovementTypeSource;
import com.surimap.domain.path.SearchPathStatus;
import java.util.UUID;

public record SearchPathSegmentUpdatedPublishRequest(
    UUID id,
    UUID incidentId,
    SearchPathStatus status,
    long version,
    UUID opId,
    UUID policePhoneId,
    UUID accountId,
    String segmentId,
    MovementType movementType,
    MovementTypeSource movementTypeSource) {}
