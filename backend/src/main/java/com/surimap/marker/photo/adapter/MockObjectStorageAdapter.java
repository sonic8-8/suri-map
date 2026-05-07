package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.port.ObjectStoragePort;
import java.util.Map;
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

  private final Map<String, Boolean> uploaded = new ConcurrentHashMap<>();

  @Override
  public String generateUploadUrl(String objectKey, String contentType, long sizeBytes) {
    return MOCK_BASE_URL + objectKey;
  }

  @Override
  public boolean exists(String objectKey) {
    return uploaded.getOrDefault(objectKey, false);
  }

  @Override
  public void delete(String objectKey) {
    uploaded.remove(objectKey);
  }

  public void simulateUpload(String objectKey) {
    uploaded.put(objectKey, true);
  }

  public void clear() {
    uploaded.clear();
  }
}
