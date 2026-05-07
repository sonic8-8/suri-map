package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.port.ObjectStoragePort;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 모의 오브젝트 스토리지 어댑터. 실제 S3 없이 upload URL/attach 흐름을 테스트한다.
 *
 * @see harness-scenarios.md §6 mock object storage fixture
 */
@Component
public class MockObjectStorageAdapter implements ObjectStoragePort {

  private static final String MOCK_BASE_URL = "http://127.0.0.1:18080/mock-upload/";
  private static final String MOCK_STORAGE_URI = "mock://object-storage/suri-map-harness";

  private final Map<String, Boolean> uploaded = new ConcurrentHashMap<>();

  @Override
  public PresignedUploadResult generatePresignedUrl(
      String objectKey, String contentType, long sizeBytes, String checksumSha256, Duration ttl) {
    return new PresignedUploadResult(
        generateUploadUrl(objectKey, contentType, sizeBytes),
        objectKey,
        MOCK_STORAGE_URI,
        Instant.now().plus(ttl),
        sizeBytes,
        contentType,
        checksumSha256);
  }

  @Override
  public String generateUploadUrl(String objectKey, String contentType, long sizeBytes) {
    return MOCK_BASE_URL + objectKey;
  }

  @Override
  public boolean exists(String objectKey) {
    return uploaded.getOrDefault(objectKey, false);
  }

  @Override
  public Optional<ObjectMetadata> headObject(String objectKey) {
    if (!exists(objectKey)) {
      return Optional.empty();
    }
    return Optional.of(new ObjectMetadata(objectKey, "application/octet-stream", 0L, null));
  }

  @Override
  public void delete(String objectKey) {
    uploaded.remove(objectKey);
  }

  @Override
  public void deleteObject(String objectKey) {
    delete(objectKey);
  }

  public void simulateUpload(String objectKey) {
    uploaded.put(objectKey, true);
  }

  public void clear() {
    uploaded.clear();
  }
}
