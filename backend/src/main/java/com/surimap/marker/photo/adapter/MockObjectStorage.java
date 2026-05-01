package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.port.ObjectStoragePort;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ObjectStoragePort의 in-memory mock 구현.
 * 개발·테스트 환경에서 external S3/MinIO 호출 없이 동작한다.
 *
 * <p>ADR-0035: 개발·하네스 object storage adapter는 MinIO(S3-compatible)를 기본으로 하고,
 * harness는 mock object storage/presigned upload fixture를 사용한다.</p>
 */
public class MockObjectStorage implements ObjectStoragePort {

    private static final String MOCK_BASE_URL = "http://mock-s3";

    private final ConcurrentHashMap<String, StoredObject> store = new ConcurrentHashMap<>();

    @Override
    public PresignedUploadResult generatePresignedUrl(String objectKey, String contentType, long sizeBytes, Duration ttl) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }

        Instant expiresAt = Instant.now().plus(ttl);
        String uploadUrl = MOCK_BASE_URL + "/" + objectKey;

        // presigned 발급 시에는 아직 실제 object가 저장되지 않음.
        // 클라이언트가 uploadUrl로 PUT 한 뒤 finalize에서 headObject로 확인하는 흐름.
        // mock에서는 simulateUpload()로 업로드를 시뮬레이션한다.
        store.put(objectKey, new StoredObject(objectKey, contentType, sizeBytes, expiresAt, false));

        return new PresignedUploadResult(uploadUrl, objectKey, expiresAt);
    }

    @Override
    public Optional<ObjectMetadata> headObject(String objectKey) {
        StoredObject obj = store.get(objectKey);
        if (obj == null) {
            return Optional.empty();
        }
        if (!obj.uploaded()) {
            return Optional.empty();
        }
        return Optional.of(new ObjectMetadata(obj.objectKey(), obj.contentType(), obj.sizeBytes()));
    }

    @Override
    public void deleteObject(String objectKey) {
        store.remove(objectKey);
    }

    // --- Mock 전용 메서드 (테스트에서만 사용) ---

    /**
     * 클라이언트가 presigned URL로 파일을 업로드한 것을 시뮬레이션한다.
     *
     * @param objectKey    업로드 대상 key
     * @param contentType  실제 업로드된 contentType
     * @param sizeBytes    실제 업로드된 파일 크기
     */
    public void simulateUpload(String objectKey, String contentType, long sizeBytes) {
        StoredObject existing = store.get(objectKey);
        if (existing == null) {
            throw new IllegalStateException("No presigned entry for key: " + objectKey);
        }
        store.put(objectKey, new StoredObject(objectKey, contentType, sizeBytes, existing.expiresAt(), true));
    }

    /**
     * presigned URL의 TTL이 만료된 것을 시뮬레이션한다.
     *
     * @param objectKey 만료시킬 key
     */
    public void simulateExpiry(String objectKey) {
        StoredObject existing = store.get(objectKey);
        if (existing == null) {
            throw new IllegalStateException("No entry for key: " + objectKey);
        }
        Instant expired = Instant.now().minusSeconds(60);
        store.put(objectKey, new StoredObject(objectKey, existing.contentType(), existing.sizeBytes(), expired, existing.uploaded()));
    }

    /**
     * 특정 key의 presigned 만료 시각을 반환한다.
     */
    public Optional<Instant> getExpiresAt(String objectKey) {
        StoredObject obj = store.get(objectKey);
        return obj == null ? Optional.empty() : Optional.of(obj.expiresAt());
    }

    /**
     * 특정 key가 TTL 만료되었는지 확인한다.
     */
    public boolean isExpired(String objectKey) {
        StoredObject obj = store.get(objectKey);
        if (obj == null) {
            return false;
        }
        return Instant.now().isAfter(obj.expiresAt());
    }

    /**
     * store를 초기화한다 (테스트 간 격리).
     */
    public void clear() {
        store.clear();
    }

    /**
     * 현재 저장된 object 수를 반환한다.
     */
    public int size() {
        return store.size();
    }

    private record StoredObject(
            String objectKey,
            String contentType,
            long sizeBytes,
            Instant expiresAt,
            boolean uploaded
    ) {}
}
