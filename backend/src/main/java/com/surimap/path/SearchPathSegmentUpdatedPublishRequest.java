package com.surimap.path;

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
