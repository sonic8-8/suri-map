package com.surimap.marker.photo.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class MarkerPhoto {

  private final UUID id;
  private final UUID markerId;
  private final String objectKey;
  private final String contentType;
  private final long sizeBytes;
  private final String checksumSha256;
  private final Instant uploadUrlExpiresAt;
  private PhotoStatus status;
  private Instant attachedAt;
  private Integer width;
  private Integer height;
  private long version;

  public MarkerPhoto(
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

  private MarkerPhoto(
      UUID id,
      UUID markerId,
      String objectKey,
      String contentType,
      long sizeBytes,
      Integer width,
      Integer height,
      String checksumSha256,
      Instant uploadUrlExpiresAt,
      PhotoStatus status,
      Instant attachedAt,
      long version) {
    this.id = Objects.requireNonNull(id);
    this.markerId = Objects.requireNonNull(markerId);
    this.objectKey = Objects.requireNonNull(objectKey);
    this.contentType = Objects.requireNonNull(contentType);
    this.sizeBytes = sizeBytes;
    this.width = width;
    this.height = height;
    this.checksumSha256 = checksumSha256;
    this.uploadUrlExpiresAt = Objects.requireNonNull(uploadUrlExpiresAt);
    this.status = Objects.requireNonNull(status);
    this.attachedAt = attachedAt;
    this.version = version;
  }

  public static MarkerPhoto rehydrate(
      UUID id,
      UUID markerId,
      String objectKey,
      String contentType,
      long sizeBytes,
      Integer width,
      Integer height,
      String checksumSha256,
      Instant uploadUrlExpiresAt,
      PhotoStatus status,
      Instant attachedAt,
      long version) {
    return new MarkerPhoto(
        id,
        markerId,
        objectKey,
        contentType,
        sizeBytes,
        width,
        height,
        checksumSha256,
        uploadUrlExpiresAt,
        status,
        attachedAt,
        version);
  }

  public UUID id() {
    return id;
  }

  public UUID markerId() {
    return markerId;
  }

  public String objectKey() {
    return objectKey;
  }

  public String contentType() {
    return contentType;
  }

  public long sizeBytes() {
    return sizeBytes;
  }

  public String checksumSha256() {
    return checksumSha256;
  }

  public Instant uploadUrlExpiresAt() {
    return uploadUrlExpiresAt;
  }

  public PhotoStatus status() {
    return status;
  }

  public Instant attachedAt() {
    return attachedAt;
  }

  public Integer width() {
    return width;
  }

  public Integer height() {
    return height;
  }

  public long version() {
    return version;
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

  public void fail() {
    status = PhotoStatus.FAILED;
    version++;
  }
}
