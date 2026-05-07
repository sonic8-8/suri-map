package com.surimap.path;

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
    List<List<Double>> geometry,
    List<SearchPathSegment> segments,
    long version,
    SearchPathStatus status) {}
