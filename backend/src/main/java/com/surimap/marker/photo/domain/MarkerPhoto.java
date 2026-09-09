package com.surimap.marker.photo.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarkerPhoto {

  private UUID id;
  private UUID markerId;
  private String objectKey;
  private String contentType;
  private long sizeBytes;
  private String checksumSha256;
  private Instant uploadUrlExpiresAt;
  private PhotoStatus status;
  private Instant attachedAt;
  private Integer width;
  private Integer height;
  private long version;

  @Builder
  private MarkerPhoto(
      UUID id,
      UUID markerId,
      String objectKey,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      Instant uploadUrlExpiresAt) {
    this.id = Objects.requireNonNull(id);
    this.markerId = Objects.requireNonNull(markerId);
    this.objectKey = Objects.requireNonNull(objectKey);
    this.contentType = Objects.requireNonNull(contentType);
    this.sizeBytes = sizeBytes;
    this.checksumSha256 = checksumSha256;
    this.uploadUrlExpiresAt = Objects.requireNonNull(uploadUrlExpiresAt);
    this.status = PhotoStatus.PENDING_UPLOAD;
    this.version = 1L;
  }

  public boolean isOpenForAttach() {
    return status == PhotoStatus.PENDING_UPLOAD;
  }

  public void attach(Instant now, Integer width, Integer height) {
    status = PhotoStatus.ATTACHED;
    attachedAt = Objects.requireNonNull(now);
    this.width = width;
    this.height = height;
    version++;
  }
}
