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
    Instant attachedAt,
    String photoUrl,
    String thumbnailUrl) {

  public MarkerPhotoSummary(
      UUID photoId,
      String status,
      long version,
      String contentType,
      long sizeBytes,
      Instant attachedAt) {
    this(photoId, status, version, contentType, sizeBytes, attachedAt, null, null);
  }
}
