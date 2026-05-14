package com.surimap.marker.dto;

import com.surimap.marker.query.MarkerPhotoSummary;
import com.surimap.marker.query.MarkerQueryResult;
import com.surimap.marker.query.MarkerView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MarkerListResponse(UUID incidentId, List<MarkerResponse> markers) {

  public static MarkerListResponse from(MarkerQueryResult result) {
    return new MarkerListResponse(
        result.incidentId(), result.markers().stream().map(MarkerResponse::from).toList());
  }

  public record MarkerResponse(
      UUID id,
      UUID incidentId,
      UUID opId,
      UUID accountId,
      UUID policePhoneId,
      String type,
      String supportRequestType,
      String source,
      String status,
      long version,
      MarkerGeoJsonPoint location,
      String memo,
      Instant occurredAt,
      List<MarkerPhotoSummaryResponse> photoSummary) {

    static MarkerResponse from(MarkerView marker) {
      return new MarkerResponse(
          marker.id(),
          marker.incidentId(),
          marker.opId(),
          marker.accountId(),
          marker.policePhoneId(),
          marker.type().name(),
          marker.supportRequestType() == null ? null : marker.supportRequestType().name(),
          marker.source().name(),
          marker.status().name(),
          marker.version(),
          MarkerGeoJsonPoint.from(marker.location()),
          marker.memo(),
          marker.occurredAt(),
          marker.photoSummary().stream().map(MarkerPhotoSummaryResponse::from).toList());
    }
  }

  public record MarkerPhotoSummaryResponse(
      UUID photoId,
      String status,
      long version,
      String contentType,
      long sizeBytes,
      Instant attachedAt) {

    static MarkerPhotoSummaryResponse from(MarkerPhotoSummary photo) {
      return new MarkerPhotoSummaryResponse(
          photo.photoId(),
          photo.status(),
          photo.version(),
          photo.contentType(),
          photo.sizeBytes(),
          photo.attachedAt());
    }
  }
}
