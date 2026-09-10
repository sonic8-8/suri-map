package com.surimap.app.service.photo.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.surimap.app.service.photo.PhotoRequestContext;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonPropertyOrder({"sizeBytes", "contentType", "width", "height", "checksumSha256"})
public class PhotoAttachServiceRequest {

  private long sizeBytes;
  private String contentType;
  private Integer width;
  private Integer height;
  private String checksumSha256;
  @JsonIgnore private UUID markerId;
  @JsonIgnore private UUID photoId;
  @JsonIgnore private PhotoRequestContext context;

  @Builder(toBuilder = true)
  private PhotoAttachServiceRequest(
      long sizeBytes,
      String contentType,
      Integer width,
      Integer height,
      String checksumSha256,
      UUID markerId,
      UUID photoId,
      PhotoRequestContext context) {
    this.sizeBytes = sizeBytes;
    this.contentType = contentType;
    this.width = width;
    this.height = height;
    this.checksumSha256 = checksumSha256;
    this.markerId = markerId;
    this.photoId = photoId;
    this.context = context;
  }
}
