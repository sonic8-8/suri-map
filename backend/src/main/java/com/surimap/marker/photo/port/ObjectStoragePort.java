package com.surimap.marker.photo.port;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * S3-compatible object storage 추상화 port. 운영: S3/MinIO adapter, 개발·하네스: MockObjectStorage.
 *
 * @see com.surimap.marker.photo.adapter.MockObjectStorage
 */
public interface ObjectStoragePort {

  Duration DEFAULT_UPLOAD_TTL = Duration.ofMinutes(15);

  /**
   * upload-url API가 사용하는 호환 메서드.
   *
   * <p>checksum/metadata fixture를 지원하는 presigned URL 계약으로 위임한다.
   */
  default String generateUploadUrl(String objectKey, String contentType, long sizeBytes) {
    return generatePresignedUrl(objectKey, contentType, sizeBytes, null, DEFAULT_UPLOAD_TTL)
        .uploadUrl();
  }

  /**
   * 업로드용 presigned URL을 발급한다.
   *
   * @param objectKey MinIO-compatible key (예: markers/{incidentId}/{markerId}/{photoId}.jpg)
   * @param contentType MIME type (image/jpeg, image/png, image/webp)
   * @param sizeBytes 허용 최대 바이트 수
   * @param ttl presigned URL 유효 기간
   * @return upload URL 결과
   */
  default PresignedUploadResult generatePresignedUrl(
      String objectKey, String contentType, long sizeBytes, Duration ttl) {
    return generatePresignedUrl(objectKey, contentType, sizeBytes, null, ttl);
  }

  /**
   * 업로드용 presigned URL을 checksum fixture와 함께 발급한다.
   *
   * @param objectKey MinIO-compatible key
   * @param contentType MIME type
   * @param sizeBytes 허용 최대 바이트 수
   * @param checksumSha256 선택 checksum
   * @param ttl upload URL TTL
   * @return upload URL 결과
   */
  PresignedUploadResult generatePresignedUrl(
      String objectKey, String contentType, long sizeBytes, String checksumSha256, Duration ttl);

  /**
   * object가 존재하는지 확인한다.
   *
   * <p>attach 서비스의 기존 흐름을 보존하되, 실제 구현은 metadata 조회 계약을 기준으로 한다.
   */
  default boolean exists(String objectKey) {
    return headObject(objectKey).isPresent();
  }

  /**
   * object가 존재하는지 확인하고 metadata를 반환한다. attach 시 실제 업로드 여부 확인용.
   *
   * @param objectKey 조회할 key
   * @return metadata, 없으면 empty
   */
  Optional<ObjectMetadata> headObject(String objectKey);

  /** object를 삭제한다. 없으면 무시한다. */
  default void delete(String objectKey) {
    deleteObject(objectKey);
  }

  /**
   * object를 삭제한다. 없으면 무시한다.
   *
   * @param objectKey 삭제할 key
   */
  void deleteObject(String objectKey);

  /** upload URL 발급 결과. */
  record PresignedUploadResult(
      String uploadUrl,
      String objectKey,
      String storageUri,
      Instant expiresAt,
      long maxSizeBytes,
      String contentType,
      String checksumSha256) {}

  /** object metadata. */
  record ObjectMetadata(
      String objectKey, String contentType, long sizeBytes, String checksumSha256) {}
}
