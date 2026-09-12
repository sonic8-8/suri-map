package com.surimap.api.service.marker.response;

import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper.AttachedPhotoRow;
import com.surimap.domain.marker.MarkerSource;
import com.surimap.domain.marker.MarkerStatus;
import com.surimap.domain.marker.MarkerSupportRequestType;
import com.surimap.domain.marker.MarkerType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Getter
@NoArgsConstructor
public class MarkersServiceResponse {

  private UUID incidentId;
  private List<MarkerServiceResponse> markers;

  @Builder
  private MarkersServiceResponse(UUID incidentId, List<MarkerServiceResponse> markers) {
    this.incidentId = incidentId;
    this.markers = List.copyOf(markers);
  }

  @Getter
  @NoArgsConstructor
  public static class MarkerServiceResponse {

    private UUID id;
    private UUID incidentId;
    private UUID opId;
    private UUID dutyShiftId;
    private UUID accountId;
    private UUID policePhoneId;
    private MarkerType type;
    private MarkerSupportRequestType supportRequestType;
    private MarkerSource source;
    private MarkerStatus status;
    private long version;
    private Point location;
    private String memo;
    private Instant occurredAt;
    private List<MarkerPhotoServiceResponse> photoSummary;

    @Builder
    private MarkerServiceResponse(
        UUID id,
        UUID incidentId,
        UUID opId,
        UUID dutyShiftId,
        UUID accountId,
        UUID policePhoneId,
        MarkerType type,
        MarkerSupportRequestType supportRequestType,
        MarkerSource source,
        MarkerStatus status,
        long version,
        Point location,
        String memo,
        Instant occurredAt,
        List<MarkerPhotoServiceResponse> photoSummary) {
      this.id = id;
      this.incidentId = incidentId;
      this.opId = opId;
      this.dutyShiftId = dutyShiftId;
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
      this.photoSummary = List.copyOf(photoSummary);
    }

    public static MarkerServiceResponse from(
        Marker marker, List<MarkerPhotoServiceResponse> photos) {
      return builder()
          .id(marker.getId())
          .incidentId(marker.getIncidentId())
          .opId(marker.getOperationalPeriodId())
          .dutyShiftId(marker.getDutyShiftId())
          .accountId(marker.getCreatedByAccountId())
          .policePhoneId(marker.getPolicePhoneId())
          .type(MarkerType.valueOf(marker.getMarkerType()))
          .supportRequestType(
              marker.getSupportRequestType() == null
                  ? null
                  : MarkerSupportRequestType.valueOf(marker.getSupportRequestType()))
          .source(MarkerSource.valueOf(marker.getMarkerSource()))
          .status(MarkerStatus.valueOf(marker.getStatus()))
          .version(marker.getVersion())
          .location(marker.getLocation())
          .memo(marker.getMemo())
          .occurredAt(marker.getOccurredAt())
          .photoSummary(photos)
          .build();
    }
  }

  @Getter
  @NoArgsConstructor
  public static class MarkerPhotoServiceResponse {

    private UUID photoId;
    private String status;
    private long version;
    private String contentType;
    private long sizeBytes;
    private Instant attachedAt;
    private String photoUrl;
    private String thumbnailUrl;

    @Builder
    private MarkerPhotoServiceResponse(
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

    public static MarkerPhotoServiceResponse from(AttachedPhotoRow photo, String photoUrl) {
      return builder()
          .photoId(photo.photoId())
          .status(photo.status())
          .version(photo.version())
          .contentType(photo.contentType())
          .sizeBytes(photo.sizeBytes())
          .attachedAt(photo.attachedAt())
          .photoUrl(photoUrl)
          .thumbnailUrl(photoUrl)
          .build();
    }
  }
}
