package com.surimap.app.controller.photo.request;

import static com.surimap.app.service.photo.PhotoService.MAX_SIZE_BYTES;

import com.surimap.app.service.photo.PhotoRequestContext;
import com.surimap.app.service.photo.request.PhotoUploadUrlServiceRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PhotoUploadUrlRequest {

  @Pattern(regexp = "image/(jpeg|png|webp)")
  private String contentType;

  @Positive
  @Max(MAX_SIZE_BYTES)
  private long sizeBytes;

  private String checksumSha256;

  @Builder
  private PhotoUploadUrlRequest(String contentType, long sizeBytes, String checksumSha256) {
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.checksumSha256 = checksumSha256;
  }

  public PhotoUploadUrlServiceRequest toServiceRequest(UUID markerId, PhotoRequestContext context) {
    return PhotoUploadUrlServiceRequest.builder()
        .markerId(markerId)
        .context(context)
        .contentType(contentType)
        .sizeBytes(sizeBytes)
        .checksumSha256(checksumSha256)
        .build();
  }
}
