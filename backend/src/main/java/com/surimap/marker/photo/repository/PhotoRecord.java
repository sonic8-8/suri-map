package com.surimap.marker.photo.repository;

import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoStatus;
import java.time.Instant;
import java.util.UUID;

public class PhotoRecord {

  private UUID id;
  private UUID markerId;
  private String objectKey;
  private String status;
  private Instant attachedAt;
  private String contentType;
  private long sizeBytes;
  private Integer width;
  private Integer height;
  private String checksumSha256;
  private Instant uploadUrlExpiresAt;
  private long version;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getMarkerId() {
    return markerId;
  }

  public void setMarkerId(UUID markerId) {
    this.markerId = markerId;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public void setObjectKey(String objectKey) {
    this.objectKey = objectKey;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getAttachedAt() {
    return attachedAt;
  }

  public void setAttachedAt(Instant attachedAt) {
    this.attachedAt = attachedAt;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  public Integer getWidth() {
    return width;
  }

  public void setWidth(Integer width) {
    this.width = width;
  }

  public Integer getHeight() {
    return height;
  }

  public void setHeight(Integer height) {
    this.height = height;
  }

  public String getChecksumSha256() {
    return checksumSha256;
  }

  public void setChecksumSha256(String checksumSha256) {
    this.checksumSha256 = checksumSha256;
  }

  public Instant getUploadUrlExpiresAt() {
    return uploadUrlExpiresAt;
  }

  public void setUploadUrlExpiresAt(Instant uploadUrlExpiresAt) {
    this.uploadUrlExpiresAt = uploadUrlExpiresAt;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }

  public MarkerPhoto toDomain() {
    return MarkerPhoto.rehydrate(
        id,
        markerId,
        objectKey,
        contentType,
        sizeBytes,
        width,
        height,
        checksumSha256,
        uploadUrlExpiresAt,
        PhotoStatus.valueOf(status),
        attachedAt,
        version);
  }
}
