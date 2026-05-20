package com.surimap.marker.repository;

import com.surimap.marker.query.MarkerPhotoSummary;
import java.time.Instant;
import java.util.UUID;

/** Internal query row for grouping attached photo summaries under marker rows. */
public record MarkerPhotoSummaryRow(
    UUID markerId,
    UUID photoId,
    String objectKey,
    String status,
    Long version,
    String contentType,
    Long sizeBytes,
    Instant attachedAt) {

  public MarkerPhotoSummary toSummary() {
    return new MarkerPhotoSummary(photoId, status, version, contentType, sizeBytes, attachedAt);
  }

  public MarkerPhotoSummary toSummary(String photoUrl) {
    return new MarkerPhotoSummary(
        photoId, status, version, contentType, sizeBytes, attachedAt, photoUrl, photoUrl);
  }
}
