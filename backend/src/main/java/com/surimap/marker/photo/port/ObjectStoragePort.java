package com.surimap.marker.photo.port;

import java.util.UUID;

/**
 * S3 호환 오브젝트 스토리지 presigned URL 발급 port.
 * Phase -1에서 MockObjectStorageAdapter가 구현한다.
 *
 * @see docs/contracts/L5-04-auth-contract-test.md §6.2
 */
public interface ObjectStoragePort {

    /**
     * 사진 업로드용 presigned URL을 발급한다.
     *
     * @param photoId   사진 ID
     * @param mimeType  MIME type (image/jpeg, image/png)
     * @param fileSize  파일 크기 (bytes)
     * @return presigned upload URL
     */
    String generatePresignedUrl(UUID photoId, String mimeType, long fileSize);

    /**
     * 업로드된 오브젝트 존재 여부를 확인한다.
     *
     * @param photoId 사진 ID
     * @return 업로드 완료 여부
     */
    boolean exists(UUID photoId);

    /**
     * 오브젝트를 삭제한다 (orphan 정리용).
     *
     * @param photoId 사진 ID
     */
    void delete(UUID photoId);
}
