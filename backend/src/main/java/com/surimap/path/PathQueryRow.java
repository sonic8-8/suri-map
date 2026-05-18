package com.surimap.path;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PathQueryRow(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID dutyShiftId,
    UUID policePhoneId,
    SearchPathStatus status,
    Instant startedAt,
    Instant endedAt,
    long version,
    @JsonSerialize(converter = LineStringGeometryJsonConverter.class)
    @JsonDeserialize(using = LineStringCoordinatesDeserializer.class)
    List<List<Double>> geometry,
    List<PathQuerySegmentRow> segments,
    List<PathExcludedPoint> excludedPoints) {}
