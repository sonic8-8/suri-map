package com.surimap.app.service.photo.response;

import com.surimap.domain.photo.MarkerPhoto;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PhotoUploadUrlServiceResponse {

  private UUID photoId;
  private String uploadUrl;
  private Instant expiresAt;
  private long maxSizeBytes;
  private long version;

  @Builder
  private PhotoUploadUrlServiceResponse(
      UUID photoId, String uploadUrl, Instant expiresAt, long maxSizeBytes, long version) {
    this.photoId = photoId;
    this.uploadUrl = uploadUrl;
    this.expiresAt = expiresAt;
    this.maxSizeBytes = maxSizeBytes;
    this.version = version;
  }

  public static PhotoUploadUrlServiceResponse from(
      MarkerPhoto photo, String uploadUrl, long maxSizeBytes) {
    return PhotoUploadUrlServiceResponse.builder()
        .photoId(photo.getId())
        .uploadUrl(uploadUrl)
        .expiresAt(photo.getUploadUrlExpiresAt())
        .maxSizeBytes(maxSizeBytes)
        .version(photo.getVersion())
        .build();
  }
}
