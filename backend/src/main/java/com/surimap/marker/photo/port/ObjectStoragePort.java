package com.surimap.marker.photo.port;

import java.time.Duration;
import java.util.Optional;

/**
 * S3-compatible object storage 추상화 port.
 * 운영: S3/MinIO adapter, 개발·하네스: MockObjectStorage.
 *
 * @see com.surimap.marker.photo.adapter.MockObjectStorage
 */
public interface ObjectStoragePort {

    /**
     * presigned upload URL을 발급한다.
     *
     * @param objectKey   MinIO-compatible key (예: markers/{incidentId}/{markerId}/{photoId}.jpg)
     * @param contentType MIME type (image/jpeg, image/png, image/webp)
     * @param sizeBytes   허용 최대 바이트 수
     * @param ttl         presigned URL 유효 기간
     * @return presigned upload 결과
     */
    PresignedUploadResult generatePresignedUrl(String objectKey, String contentType, long sizeBytes, Duration ttl);

    /**
     * object가 존재하는지 확인하고 metadata를 반환한다.
     * finalize 시 실제 업로드 여부 확인용.
     *
     * @param objectKey 조회할 key
     * @return metadata, 없으면 empty
     */
    Optional<ObjectMetadata> headObject(String objectKey);

    /**
     * object를 삭제한다. 없으면 무시한다.
     *
     * @param objectKey 삭제할 key
     */
    void deleteObject(String objectKey);

    /**
     * presigned URL 발급 결과.
     */
    record PresignedUploadResult(
            String uploadUrl,
            String objectKey,
            java.time.Instant expiresAt
    ) {}

    /**
     * object metadata.
     */
    record ObjectMetadata(
            String objectKey,
            String contentType,
            long sizeBytes
    ) {}
}
