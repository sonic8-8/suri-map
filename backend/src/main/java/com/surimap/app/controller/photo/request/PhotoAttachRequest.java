package com.surimap.app.controller.photo.request;

import static com.surimap.app.service.photo.PhotoService.MAX_SIZE_BYTES;

import com.surimap.app.service.photo.PhotoRequestContext;
import com.surimap.app.service.photo.request.PhotoAttachServiceRequest;
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
public class PhotoAttachRequest {

  @Positive
  @Max(MAX_SIZE_BYTES)
  private long sizeBytes;

  @NotNull
  @Pattern(regexp = "image/(jpeg|png|webp)")
  private String contentType;

  private Integer width;
  private Integer height;
  private String checksumSha256;

  @Builder(toBuilder = true)
  private PhotoAttachRequest(
      long sizeBytes, String contentType, Integer width, Integer height, String checksumSha256) {
    this.sizeBytes = sizeBytes;
    this.contentType = contentType;
    this.width = width;
    this.height = height;
    this.checksumSha256 = checksumSha256;
  }

  public PhotoAttachServiceRequest toServiceRequest(
      UUID markerId, UUID photoId, PhotoRequestContext context) {
    return PhotoAttachServiceRequest.builder()
        .sizeBytes(sizeBytes)
        .contentType(contentType)
        .width(width)
        .height(height)
        .checksumSha256(checksumSha256)
        .markerId(markerId)
        .photoId(photoId)
        .context(context)
        .build();
  }
}
