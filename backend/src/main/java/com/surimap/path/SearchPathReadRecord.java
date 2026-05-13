package com.surimap.path;

import java.time.Instant;
import java.util.UUID;
import org.locationtech.jts.geom.Geometry;

public record SearchPathReadRecord(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID dutyShiftId,
    UUID policePhoneId,
    String status,
    Instant startedAt,
    Instant endedAt,
    Geometry geometry,
    long version) {}
