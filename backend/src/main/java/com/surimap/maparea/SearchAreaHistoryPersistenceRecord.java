package com.surimap.maparea;

import java.time.Instant;
import java.util.UUID;
import org.locationtech.jts.geom.Polygon;

public record SearchAreaHistoryPersistenceRecord(
    UUID id,
    UUID searchAreaId,
    String changeType,
    String previousStatus,
    String nextStatus,
    Polygon previousGeometry,
    Polygon nextGeometry,
    String changeMemo,
    UUID changedByAccountId,
    Instant changedAt,
    Instant createdAt) {}
