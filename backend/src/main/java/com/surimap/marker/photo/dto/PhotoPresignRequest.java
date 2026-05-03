package com.surimap.marker.photo.dto;

import java.util.UUID;

/**
 * 사진 presign 요청 DTO.
 */
public record PhotoPresignRequest(
        UUID markerId,
        String mimeType,
        long fileSize
) {
    /** 허용 MIME types */
    private static final java.util.Set<String> ALLOWED_MIME_TYPES =
            java.util.Set.of("image/jpeg", "image/png", "image/webp");

    /** 최대 파일 크기: 10MB */
    public static final long MAX_FILE_SIZE = 10_485_760L;

    /** 최대 사진 수 (마커당) */
    public static final int MAX_PHOTOS_PER_MARKER = 10;

    public void validate() {
        if (markerId == null) {
            throw new IllegalArgumentException("markerId is required");
        }
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("unsupported mime type: " + mimeType);
        }
        if (fileSize <= 0 || fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("file size out of range: " + fileSize);
        }
    }
}
