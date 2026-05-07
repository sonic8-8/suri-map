package com.surimap.marker.query;

import java.time.Instant;
import java.util.UUID;

/** Attached photo summary for MarkerQuery.byIncident consumers. */
public record MarkerPhotoSummary(
    UUID photoId,
    String status,
    long version,
    String contentType,
    long sizeBytes,
    Instant attachedAt) {}
