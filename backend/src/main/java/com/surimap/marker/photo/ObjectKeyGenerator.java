package com.surimap.marker.photo;

import java.util.Map;
import java.util.UUID;

/**
 * MinIO-compatible object key 생성기. S5.json photo entity의 object_key 규칙을 따른다.
 *
 * <p>형식: {@code markers/{incidentId}/{markerId}/{photoId}.{extension}}
 */
public class ObjectKeyGenerator {

  private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/webp", "webp");

  /**
   * object key를 생성한다.
   *
   * @param incidentId 사건 ID
   * @param markerId 마커 ID
   * @param photoId 사진 ID
   * @param contentType MIME type
   * @return MinIO-compatible object key
   * @throws IllegalArgumentException 지원하지 않는 contentType
   */
  public String generate(UUID incidentId, UUID markerId, UUID photoId, String contentType) {
    if (incidentId == null || markerId == null || photoId == null) {
      throw new IllegalArgumentException("incidentId, markerId, photoId must not be null");
    }
    return generate(incidentId.toString(), markerId.toString(), photoId.toString(), contentType);
  }

  /**
   * 하네스 fixture ID 기반 object key를 생성한다.
   *
   * @param incidentId 사건 fixture ID
   * @param markerId 마커 fixture ID
   * @param photoId 사진 fixture ID
   * @param contentType MIME type
   * @return MinIO-compatible object key
   */
  public String generate(String incidentId, String markerId, String photoId, String contentType) {
    validateNamespaceSegment("incidentId", incidentId);
    validateNamespaceSegment("markerId", markerId);
    validateNamespaceSegment("photoId", photoId);
    if (contentType == null || !CONTENT_TYPE_TO_EXTENSION.containsKey(contentType)) {
      throw new IllegalArgumentException(
          "Unsupported contentType: "
              + contentType
              + ". Allowed: "
              + CONTENT_TYPE_TO_EXTENSION.keySet());
    }

    String extension = CONTENT_TYPE_TO_EXTENSION.get(contentType);
    return "markers/" + incidentId + "/" + markerId + "/" + photoId + "." + extension;
  }

  /**
   * contentType에서 파일 확장자를 추출한다.
   *
   * @param contentType MIME type
   * @return 확장자 (jpg, png, webp)
   */
  public String extensionFor(String contentType) {
    String ext = CONTENT_TYPE_TO_EXTENSION.get(contentType);
    if (ext == null) {
      throw new IllegalArgumentException("Unsupported contentType: " + contentType);
    }
    return ext;
  }

  private void validateNamespaceSegment(String name, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    if (value.contains("/") || value.contains("\\")) {
      throw new IllegalArgumentException(name + " must be a single object key segment");
    }
  }
}
