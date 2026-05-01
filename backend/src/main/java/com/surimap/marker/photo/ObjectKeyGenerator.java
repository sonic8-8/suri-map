package com.surimap.marker.photo;

import java.util.Map;
import java.util.UUID;

/**
 * MinIO-compatible object key 생성기.
 * S5.json photo entity의 object_key 규칙을 따른다.
 *
 * <p>형식: {@code markers/{incidentId}/{markerId}/{photoId}.{extension}}</p>
 */
public class ObjectKeyGenerator {

    private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    /**
     * object key를 생성한다.
     *
     * @param incidentId  사건 ID
     * @param markerId    마커 ID
     * @param photoId     사진 ID
     * @param contentType MIME type
     * @return MinIO-compatible object key
     * @throws IllegalArgumentException 지원하지 않는 contentType
     */
    public String generate(UUID incidentId, UUID markerId, UUID photoId, String contentType) {
        if (incidentId == null || markerId == null || photoId == null) {
            throw new IllegalArgumentException("incidentId, markerId, photoId must not be null");
        }
        if (contentType == null || !CONTENT_TYPE_TO_EXTENSION.containsKey(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported contentType: " + contentType
                            + ". Allowed: " + CONTENT_TYPE_TO_EXTENSION.keySet()
            );
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
}
