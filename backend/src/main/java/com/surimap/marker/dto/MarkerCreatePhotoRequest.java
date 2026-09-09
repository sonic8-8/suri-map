package com.surimap.marker.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonPropertyOrder({"photoId", "sizeBytes", "contentType", "width", "height", "checksumSha256"})
public class MarkerCreatePhotoRequest {

  private UUID photoId;
  private long sizeBytes;
  private String contentType;
  private Integer width;
  private Integer height;
  private String checksumSha256;

  @Builder
  private MarkerCreatePhotoRequest(
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
}
