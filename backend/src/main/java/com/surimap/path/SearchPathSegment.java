package com.surimap.path;

public record SearchPathSegment(
    String id,
    MovementType movementType,
    int startIndex,
    int endIndex,
    String startPointId,
    String endPointId) {}
