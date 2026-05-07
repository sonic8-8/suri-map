package com.surimap.marker.repository;

import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

public record MarkerUpdateRecord(
    UUID id,
    long expectedVersion,
    MarkerType markerType,
    Point location,
    String memo,
    MarkerStatus status,
    long version) {}
