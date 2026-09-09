package com.surimap.marker.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonPropertyOrder({"photoId", "status", "version", "markerId", "markerVersion"})
public class MarkerCreatePhotoResponse {

  private UUID photoId;
  private String status;
  private long version;
  private UUID markerId;
  private long markerVersion;

  @Builder
  private MarkerCreatePhotoResponse(
      UUID photoId, String status, long version, UUID markerId, long markerVersion) {
    this.photoId = photoId;
    this.status = status;
    this.version = version;
    this.markerId = markerId;
    this.markerVersion = markerVersion;
  }
}
