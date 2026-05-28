package com.surimap.maparea;

import java.time.Instant;
import java.util.UUID;
import org.locationtech.jts.geom.Polygon;

public record SearchAreaPersistenceRecord(
    UUID id,
    UUID operationalPeriodId,
    UUID parentSearchAreaId,
    String name,
    String areaLevel,
    String colorToken,
    Polygon geometry,
    String status,
    long version,
    UUID createdByAccountId,
    Instant createdAt,
    Instant updatedAt) {}
