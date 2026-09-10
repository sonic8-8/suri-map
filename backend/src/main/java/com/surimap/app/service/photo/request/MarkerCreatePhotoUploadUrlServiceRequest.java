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
@JsonPropertyOrder({"markerId", "incidentId", "opId", "contentType", "sizeBytes", "checksumSha256"})
public class MarkerCreatePhotoUploadUrlServiceRequest {

  private UUID markerId;
  private UUID incidentId;
  private UUID opId;
  private String contentType;
  private long sizeBytes;
  private String checksumSha256;
  @JsonIgnore private PhotoRequestContext context;

  @Builder(toBuilder = true)
  private MarkerCreatePhotoUploadUrlServiceRequest(
      UUID markerId,
      UUID incidentId,
      UUID opId,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      PhotoRequestContext context) {
    this.markerId = markerId;
    this.incidentId = incidentId;
    this.opId = opId;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.checksumSha256 = checksumSha256;
    this.context = context;
  }
}
