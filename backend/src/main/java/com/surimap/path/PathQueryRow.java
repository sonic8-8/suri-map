package com.surimap.path;

import java.util.List;
import java.util.UUID;

public record PathQueryRow(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID policePhoneId,
    SearchPathStatus status,
    long version,
    List<List<Double>> geometry,
    List<SearchPathSegment> segments,
    List<PathExcludedPoint> excludedPoints) {}
