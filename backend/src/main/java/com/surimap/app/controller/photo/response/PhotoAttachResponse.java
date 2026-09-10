package com.surimap.app.controller.photo.response;

import com.surimap.app.service.photo.response.PhotoAttachServiceResponse;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PhotoAttachResponse {

  private UUID photoId;
  private String status;
  private long version;
  private UUID markerId;
  private long markerVersion;

  @Builder(toBuilder = true)
  private PhotoAttachResponse(
      UUID photoId, String status, long version, UUID markerId, long markerVersion) {
    this.photoId = photoId;
    this.status = status;
    this.version = version;
    this.markerId = markerId;
    this.markerVersion = markerVersion;
  }

  public static PhotoAttachResponse from(PhotoAttachServiceResponse response) {
    return PhotoAttachResponse.builder()
        .photoId(response.getPhotoId())
        .status(response.getStatus())
        .version(response.getVersion())
        .markerId(response.getMarkerId())
        .markerVersion(response.getMarkerVersion())
        .build();
  }
}
