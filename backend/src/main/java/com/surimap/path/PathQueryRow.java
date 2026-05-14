package com.surimap.path;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import java.util.UUID;

public record PathQueryRow(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID policePhoneId,
    SearchPathStatus status,
    long version,
    @JsonSerialize(converter = LineStringGeometryJsonConverter.class)
    @JsonDeserialize(using = LineStringCoordinatesDeserializer.class)
    List<List<Double>> geometry,
    List<SearchPathSegment> segments,
    List<PathExcludedPoint> excludedPoints) {}
