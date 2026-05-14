package com.surimap.path;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import java.util.UUID;

public record PathBatchAppendResponse(
    UUID id,
    UUID dutyShiftId,
    UUID opId,
    UUID policePhoneId,
    int acceptedPointCount,
    int excludedPointCount,
    List<PathExcludedPoint> excludedPoints,
    @JsonSerialize(converter = LineStringGeometryJsonConverter.class)
    @JsonDeserialize(using = LineStringCoordinatesDeserializer.class)
    List<List<Double>> geometry,
    List<SearchPathSegment> segments,
    long version,
    SearchPathStatus status) {}
