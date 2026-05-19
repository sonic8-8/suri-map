package com.surimap.maparea.boundary;

import java.time.Instant;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

public record SearchAreaBoundaryAlertPersistenceRecord(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID searchAreaId,
    UUID policePhoneId,
    UUID searchPathId,
    String alertType,
    String status,
    Point location,
    Instant clientTs,
    Instant serverReceivedAt,
    long version,
    Instant createdAt) {}
