package com.surimap.api.controller.marker.response;

import com.surimap.api.service.marker.response.MarkersServiceResponse;
import com.surimap.api.service.marker.response.MarkersServiceResponse.MarkerPhotoServiceResponse;
import com.surimap.api.service.marker.response.MarkersServiceResponse.MarkerServiceResponse;
import com.surimap.global.geometry.GeoJsonPoint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MarkersResponse {

  private UUID incidentId;
  private List<MarkerResponse> markers;

  @Builder
  private MarkersResponse(UUID incidentId, List<MarkerResponse> markers) {
    this.incidentId = incidentId;
    this.markers = markers;
  }

  public static MarkersResponse from(MarkersServiceResponse response) {
    return MarkersResponse.builder()
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
    private GeoJsonPoint location;
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
        GeoJsonPoint location,
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

    static MarkerResponse from(MarkerServiceResponse marker) {
      return MarkerResponse.builder()
          .id(marker.getId())
          .incidentId(marker.getIncidentId())
          .opId(marker.getOpId())
          .accountId(marker.getAccountId())
          .policePhoneId(marker.getPolicePhoneId())
          .type(marker.getType().name())
          .supportRequestType(
              marker.getSupportRequestType() == null ? null : marker.getSupportRequestType().name())
          .source(marker.getSource().name())
          .status(marker.getStatus().name())
          .version(marker.getVersion())
          .location(GeoJsonPoint.from(marker.getLocation()))
          .memo(marker.getMemo())
          .occurredAt(marker.getOccurredAt())
          .photoSummary(
              marker.getPhotoSummary().stream().map(MarkerPhotoSummaryResponse::from).toList())
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

    static MarkerPhotoSummaryResponse from(MarkerPhotoServiceResponse photo) {
      return MarkerPhotoSummaryResponse.builder()
          .photoId(photo.getPhotoId())
          .status(photo.getStatus())
          .version(photo.getVersion())
          .contentType(photo.getContentType())
          .sizeBytes(photo.getSizeBytes())
          .attachedAt(photo.getAttachedAt())
          .photoUrl(photo.getPhotoUrl())
          .thumbnailUrl(photo.getThumbnailUrl())
          .build();
    }
  }
}
