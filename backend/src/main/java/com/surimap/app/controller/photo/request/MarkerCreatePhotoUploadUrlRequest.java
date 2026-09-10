package com.surimap.app.controller.photo.request;

import static com.surimap.app.service.photo.PhotoService.MAX_SIZE_BYTES;

import com.surimap.app.service.photo.PhotoRequestContext;
import com.surimap.app.service.photo.request.MarkerCreatePhotoUploadUrlServiceRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MarkerCreatePhotoUploadUrlRequest {

  @NotNull private UUID markerId;
  @NotNull private UUID incidentId;
  @NotNull private UUID opId;

  @NotNull
  @Pattern(regexp = "image/(jpeg|png|webp)")
  private String contentType;

  @Positive
  @Max(MAX_SIZE_BYTES)
  private long sizeBytes;

  private String checksumSha256;

  @Builder(toBuilder = true)
  private MarkerCreatePhotoUploadUrlRequest(
      UUID markerId,
      UUID incidentId,
      UUID opId,
      String contentType,
      long sizeBytes,
      String checksumSha256) {
    this.markerId = markerId;
    this.incidentId = incidentId;
    this.opId = opId;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.checksumSha256 = checksumSha256;
  }

  public MarkerCreatePhotoUploadUrlServiceRequest toServiceRequest(PhotoRequestContext context) {
    return MarkerCreatePhotoUploadUrlServiceRequest.builder()
        .markerId(markerId)
        .incidentId(incidentId)
        .opId(opId)
        .contentType(contentType)
        .sizeBytes(sizeBytes)
        .checksumSha256(checksumSha256)
        .context(context)
        .build();
  }
}
