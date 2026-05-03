package com.surimap.marker.photo.service;

import com.surimap.marker.photo.dto.PhotoFinalizeResponse;
import com.surimap.marker.photo.dto.PhotoPresignRequest;
import com.surimap.marker.photo.dto.PhotoPresignResponse;
import com.surimap.marker.photo.port.ObjectStoragePort;

import java.util.UUID;

/**
 * 사진 presign/finalize 서비스.
 *
 * 흐름:
 * 1. presign: photoId 발급 + presigned URL 생성 + photo row PENDING 저장
 * 2. (클라이언트가 presigned URL로 직접 업로드)
 * 3. finalize: 오브젝트 존재 확인 + photo row ACTIVE 전환
 *
 * @see harness-scenarios.md SC-06 사진 첨부 흐름
 */
public class PhotoService {

    private final ObjectStoragePort storagePort;

    public PhotoService(ObjectStoragePort storagePort) {
        this.storagePort = storagePort;
    }

    /**
     * presigned URL 발급.
     * photo row를 PENDING 상태로 생성하고 업로드 URL을 반환한다.
     */
    public PhotoPresignResponse presign(UUID markerId, PhotoPresignRequest request) {
        request.validate();

        UUID photoId = UUID.randomUUID();
        String uploadUrl = storagePort.generatePresignedUrl(
                photoId, request.mimeType(), request.fileSize());

        // TODO: photo row INSERT (status=PENDING, version=1)
        // photoRepository.save(new Photo(photoId, markerId, "PENDING", 1));

        return new PhotoPresignResponse(photoId, uploadUrl, 3600L);
    }

    /**
     * 업로드 완료 확인 (finalize).
     * 오브젝트가 존재하면 photo row를 ACTIVE로 전환한다.
     */
    public PhotoFinalizeResponse finalize(UUID markerId, UUID photoId) {
        // 1. 오브젝트 존재 확인
        if (!storagePort.exists(photoId)) {
            throw new IllegalStateException("finalize_missing_blob: photo not uploaded yet");
        }

        // TODO: photo row 조회 + status PENDING→ACTIVE 전환
        // Photo photo = photoRepository.findById(photoId).orElseThrow();
        // if (!"PENDING".equals(photo.getStatus())) throw duplicate finalize
        // photo.setStatus("ACTIVE"); photo.setVersion(photo.getVersion() + 1);

        return new PhotoFinalizeResponse(photoId, markerId, "ACTIVE", 1L);
    }
}
