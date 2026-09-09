package com.surimap.app.controller.photo.response;

import com.surimap.app.service.photo.response.PhotoUploadUrlServiceResponse;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PhotoUploadUrlResponse {

  private UUID photoId;
  private String uploadUrl;
  private Instant expiresAt;
  private long maxSizeBytes;
  private long version;

  @Builder
  private PhotoUploadUrlResponse(
      UUID photoId, String uploadUrl, Instant expiresAt, long maxSizeBytes, long version) {
    this.photoId = photoId;
    this.uploadUrl = uploadUrl;
    this.expiresAt = expiresAt;
    this.maxSizeBytes = maxSizeBytes;
    this.version = version;
  }

  public static PhotoUploadUrlResponse from(PhotoUploadUrlServiceResponse response) {
    return PhotoUploadUrlResponse.builder()
        .photoId(response.getPhotoId())
        .uploadUrl(response.getUploadUrl())
        .expiresAt(response.getExpiresAt())
        .maxSizeBytes(response.getMaxSizeBytes())
        .version(response.getVersion())
        .build();
  }
}
