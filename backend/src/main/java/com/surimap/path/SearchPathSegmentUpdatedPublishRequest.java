package com.surimap.path;

import java.util.UUID;

public record SearchPathSegmentUpdatedPublishRequest(
    UUID id,
    SearchPathStatus status,
    long version,
    UUID opId,
    UUID policePhoneId,
    String segmentId,
    MovementType movementType,
    MovementTypeSource movementTypeSource) {}
