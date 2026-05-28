package com.surimap.maparea;

import java.time.Instant;
import java.util.UUID;
import org.locationtech.jts.geom.Polygon;

public record SearchAreaReadRecord(
    UUID id,
    UUID incidentId,
    UUID operationalPeriodId,
    UUID parentSearchAreaId,
    String areaLevel,
    String colorToken,
    String status,
    long version,
    Polygon geometry,
    Instant updatedAt,
    long historyCount) {}
