package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.port.ObjectStoragePort;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 모의 오브젝트 스토리지 어댑터.
 * 실제 S3 없이 presign/finalize 흐름을 테스트한다.
 *
 * @see harness-scenarios.md §6 mock object storage fixture
 */
public class MockObjectStorageAdapter implements ObjectStoragePort {

    private static final String MOCK_BASE_URL = "http://127.0.0.1:18080/mock-upload/";

    /** 업로드 완료 상태 저장소 */
    private final Map<UUID, Boolean> uploaded = new ConcurrentHashMap<>();

    @Override
    public String generatePresignedUrl(UUID photoId, String mimeType, long fileSize) {
        return MOCK_BASE_URL + photoId;
    }

    @Override
    public boolean exists(UUID photoId) {
        return uploaded.getOrDefault(photoId, false);
    }

    @Override
    public void delete(UUID photoId) {
        uploaded.remove(photoId);
    }

    // ── 테스트 헬퍼 ──

    /** 업로드 완료 시뮬레이션 */
    public void simulateUpload(UUID photoId) {
        uploaded.put(photoId, true);
    }

    /** 전체 초기화 */
    public void clear() {
        uploaded.clear();
    }
}
