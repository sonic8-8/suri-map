package com.surimap.marker.photo.port;

/** S3-compatible object storage upload URL 발급 port. */
public interface ObjectStoragePort {

  String generateUploadUrl(String objectKey, String contentType, long sizeBytes);

  boolean exists(String objectKey);

  void delete(String objectKey);
}
