package com.surimap.app.service.photo.request;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonPropertyOrder({"photoId", "sizeBytes", "contentType", "width", "height", "checksumSha256"})
public class MarkerCreatePhotoServiceRequest {

  private UUID photoId;
  private long sizeBytes;
  private String contentType;
  private Integer width;
  private Integer height;
  private String checksumSha256;

  @Builder
  private MarkerCreatePhotoServiceRequest(
      UUID photoId,
      long sizeBytes,
      String contentType,
      Integer width,
      Integer height,
      String checksumSha256) {
    this.photoId = photoId;
    this.sizeBytes = sizeBytes;
    this.contentType = contentType;
    this.width = width;
    this.height = height;
    this.checksumSha256 = checksumSha256;
  }

  public PhotoAttachServiceRequest toPhotoAttachServiceRequest(UUID markerId) {
    return PhotoAttachServiceRequest.builder()
        .markerId(markerId)
        .photoId(photoId)
        .sizeBytes(sizeBytes)
        .contentType(contentType)
        .width(width)
        .height(height)
        .checksumSha256(checksumSha256)
        .build();
  }
}
