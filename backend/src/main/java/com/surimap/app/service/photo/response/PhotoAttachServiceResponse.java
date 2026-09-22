package com.surimap.app.service.photo.response;

import com.surimap.domain.photo.MarkerPhoto;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PhotoAttachServiceResponse {

  private UUID photoId;
  private String status;
  private long version;
  private UUID markerId;
  private long markerVersion;

  @Builder(toBuilder = true)
  private PhotoAttachServiceResponse(
      UUID photoId, String status, long version, UUID markerId, long markerVersion) {
    this.photoId = photoId;
    this.status = status;
    this.version = version;
    this.markerId = markerId;
    this.markerVersion = markerVersion;
  }

  public static PhotoAttachServiceResponse from(MarkerPhoto photo, long markerVersion) {
    return PhotoAttachServiceResponse.builder()
        .photoId(photo.getId())
        .status(photo.getStatus().name())
        .version(photo.getVersion())
        .markerId(photo.getMarkerId())
        .markerVersion(markerVersion)
        .build();
  }
}
