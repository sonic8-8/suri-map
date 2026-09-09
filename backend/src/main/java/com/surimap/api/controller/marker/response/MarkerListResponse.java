package com.surimap.api.controller.marker.response;

import com.surimap.api.service.marker.response.MarkerListServiceResponse;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.query.MarkerPhotoSummary;
import com.surimap.marker.query.MarkerView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MarkerListResponse {

  private UUID incidentId;
  private List<MarkerResponse> markers;

  @Builder
  private MarkerListResponse(UUID incidentId, List<MarkerResponse> markers) {
    this.incidentId = incidentId;
    this.markers = markers;
  }

  public static MarkerListResponse from(MarkerListServiceResponse response) {
    return MarkerListResponse.builder()
        .incidentId(response.getIncidentId())
        .markers(response.getMarkers().stream().map(MarkerResponse::from).toList())
        .build();
  }

  @Getter
  @NoArgsConstructor
  public static class MarkerResponse {

    private UUID id;
    private UUID incidentId;
    private UUID opId;
    private UUID accountId;
    private UUID policePhoneId;
    private String type;
    private String supportRequestType;
    private String source;
    private String status;
    private long version;
    private MarkerGeoJsonPoint location;
    private String memo;
    private Instant occurredAt;
    private List<MarkerPhotoSummaryResponse> photoSummary;

    @Builder
    private MarkerResponse(
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
      this.id = id;
      this.incidentId = incidentId;
      this.opId = opId;
      this.accountId = accountId;
      this.policePhoneId = policePhoneId;
      this.type = type;
      this.supportRequestType = supportRequestType;
      this.source = source;
      this.status = status;
      this.version = version;
      this.location = location;
      this.memo = memo;
      this.occurredAt = occurredAt;
      this.photoSummary = photoSummary;
    }

    static MarkerResponse from(MarkerView marker) {
      return MarkerResponse.builder()
          .id(marker.id())
          .incidentId(marker.incidentId())
          .opId(marker.opId())
          .accountId(marker.accountId())
          .policePhoneId(marker.policePhoneId())
          .type(marker.type().name())
          .supportRequestType(
              marker.supportRequestType() == null ? null : marker.supportRequestType().name())
          .source(marker.source().name())
          .status(marker.status().name())
          .version(marker.version())
          .location(MarkerGeoJsonPoint.from(marker.location()))
          .memo(marker.memo())
          .occurredAt(marker.occurredAt())
          .photoSummary(
              marker.photoSummary().stream().map(MarkerPhotoSummaryResponse::from).toList())
          .build();
    }
  }

  @Getter
  @NoArgsConstructor
  public static class MarkerPhotoSummaryResponse {

    private UUID photoId;
    private String status;
    private long version;
    private String contentType;
    private long sizeBytes;
    private Instant attachedAt;
    private String photoUrl;
    private String thumbnailUrl;

    @Builder
    private MarkerPhotoSummaryResponse(
        UUID photoId,
        String status,
        long version,
        String contentType,
        long sizeBytes,
        Instant attachedAt,
        String photoUrl,
        String thumbnailUrl) {
      this.photoId = photoId;
      this.status = status;
      this.version = version;
      this.contentType = contentType;
      this.sizeBytes = sizeBytes;
      this.attachedAt = attachedAt;
      this.photoUrl = photoUrl;
      this.thumbnailUrl = thumbnailUrl;
    }

    static MarkerPhotoSummaryResponse from(MarkerPhotoSummary photo) {
      return MarkerPhotoSummaryResponse.builder()
          .photoId(photo.photoId())
          .status(photo.status())
          .version(photo.version())
          .contentType(photo.contentType())
          .sizeBytes(photo.sizeBytes())
          .attachedAt(photo.attachedAt())
          .photoUrl(photo.photoUrl())
          .thumbnailUrl(photo.thumbnailUrl())
          .build();
    }
  }
}
