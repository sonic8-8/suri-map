package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.port.ObjectStoragePort;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 모의 오브젝트 스토리지 어댑터. 실제 S3 없이 upload URL/attach 흐름을 테스트한다.
 *
 * @see harness-scenarios.md §6 mock object storage fixture
 */
@Component
@ConditionalOnProperty(
    name = "surimap.object-storage.provider",
    havingValue = "mock",
    matchIfMissing = true)
public class MockObjectStorageAdapter implements ObjectStoragePort {

  private static final String MOCK_BASE_URL = "http://127.0.0.1:18080/mock-upload/";
  private static final String MOCK_STORAGE_URI = "mock://object-storage/suri-map-harness";

  private final Map<String, ObjectMetadata> issued = new ConcurrentHashMap<>();
  private final Map<String, ObjectMetadata> uploaded = new ConcurrentHashMap<>();
  private final Map<String, byte[]> uploadedBytes = new ConcurrentHashMap<>();

  @Override
  public PresignedUploadResult generatePresignedUrl(
      String objectKey, String contentType, long sizeBytes, String checksumSha256, Duration ttl) {
    issued.put(objectKey, new ObjectMetadata(objectKey, contentType, sizeBytes, checksumSha256));
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
  public Optional<String> generatePresignedViewUrl(String objectKey, Duration ttl) {
    if (objectKey == null || objectKey.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(MOCK_BASE_URL + objectKey);
  }

  @Override
  public boolean exists(String objectKey) {
    return uploaded.containsKey(objectKey);
  }

  @Override
  public Optional<ObjectMetadata> headObject(String objectKey) {
    return Optional.ofNullable(uploaded.get(objectKey));
  }

  @Override
  public void delete(String objectKey) {
    uploaded.remove(objectKey);
    uploadedBytes.remove(objectKey);
  }

  @Override
  public void deleteObject(String objectKey) {
    delete(objectKey);
  }

  public void simulateUpload(String objectKey) {
    ObjectMetadata metadata =
        issued.getOrDefault(
            objectKey, new ObjectMetadata(objectKey, "application/octet-stream", 0L, null));
    uploaded.put(objectKey, metadata);
  }

  public void simulateUpload(String objectKey, String contentType, long sizeBytes) {
    ObjectMetadata issuedMetadata =
        issued.getOrDefault(objectKey, new ObjectMetadata(objectKey, contentType, sizeBytes, null));
    String resolvedContentType =
        contentType == null || contentType.isBlank() ? issuedMetadata.contentType() : contentType;
    uploaded.put(
        objectKey,
        new ObjectMetadata(
            objectKey, resolvedContentType, sizeBytes, issuedMetadata.checksumSha256()));
  }

  public void simulateUpload(String objectKey, String contentType, byte[] body) {
    byte[] uploadBytes = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
    simulateUpload(objectKey, contentType, uploadBytes.length);
    uploadedBytes.put(objectKey, uploadBytes);
  }

  public Optional<byte[]> readObject(String objectKey) {
    byte[] body = uploadedBytes.get(objectKey);
    return body == null ? Optional.empty() : Optional.of(Arrays.copyOf(body, body.length));
  }

  public void clear() {
    issued.clear();
    uploaded.clear();
    uploadedBytes.clear();
  }
}
