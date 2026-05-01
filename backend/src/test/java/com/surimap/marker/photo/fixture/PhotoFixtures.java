package com.surimap.marker.photo.fixture;

import java.time.Instant;
import java.util.UUID;

/**
 * S5.json photo entity의 상태 전이 시나리오를 위한 fixture factory.
 * DB 없이 도메인 로직과 mock adapter 테스트에서 사용한다.
 *
 * <p>photo 상태 전이: PRESIGNED → UPLOADED → FINALIZED, PRESIGNED → FAILED</p>
 */
public final class PhotoFixtures {

    /** 테스트용 고정 UUID */
    public static final UUID INCIDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID MARKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    public static final UUID PHOTO_ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000101");
    public static final UUID PHOTO_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000102");

    /** 기본 사진 metadata */
    public static final String JPEG_CONTENT_TYPE = "image/jpeg";
    public static final String PNG_CONTENT_TYPE = "image/png";
    public static final String WEBP_CONTENT_TYPE = "image/webp";
    public static final long VALID_SIZE_BYTES = 2_000_000L;           // 2MB
    public static final long MAX_SIZE_BYTES = 10_485_760L;            // 10MB (S5.json 제한)
    public static final long OVERSIZED_BYTES = 10_485_761L;           // 10MB + 1

    private PhotoFixtures() {}

    /**
     * PRESIGNED 상태의 photo context.
     * presigned URL이 발급되었지만 아직 업로드되지 않은 상태.
     */
    public static PhotoContext presignedPhoto() {
        return new PhotoContext(
                PHOTO_ID_1, MARKER_ID, INCIDENT_ID,
                JPEG_CONTENT_TYPE, VALID_SIZE_BYTES,
                "PRESIGNED", Instant.now().plusSeconds(900), // TTL 15분
                null
        );
    }

    /**
     * UPLOADED 상태의 photo context.
     * 클라이언트가 presigned URL로 업로드를 완료했지만 finalize 전.
     */
    public static PhotoContext uploadedPhoto() {
        return new PhotoContext(
                PHOTO_ID_1, MARKER_ID, INCIDENT_ID,
                JPEG_CONTENT_TYPE, VALID_SIZE_BYTES,
                "UPLOADED", Instant.now().plusSeconds(600),
                null
        );
    }

    /**
     * FINALIZED 상태의 photo context.
     * 업로드 완료 후 metadata 검증이 통과된 최종 상태.
     */
    public static PhotoContext finalizedPhoto() {
        return new PhotoContext(
                PHOTO_ID_1, MARKER_ID, INCIDENT_ID,
                JPEG_CONTENT_TYPE, VALID_SIZE_BYTES,
                "FINALIZED", null,
                Instant.now()
        );
    }

    /**
     * FAILED: TTL 만료로 실패한 photo context.
     * presigned URL이 만료된 후 finalize 시도 → 409 write_conflict.
     */
    public static PhotoContext failedPhoto_ttlExpired() {
        return new PhotoContext(
                PHOTO_ID_1, MARKER_ID, INCIDENT_ID,
                JPEG_CONTENT_TYPE, VALID_SIZE_BYTES,
                "FAILED", Instant.now().minusSeconds(60), // 이미 만료
                null
        );
    }

    /**
     * FAILED: 크기 불일치 photo context.
     * presign 시 요청한 크기와 실제 업로드된 크기가 다름.
     */
    public static PhotoContext failedPhoto_sizeMismatch() {
        return new PhotoContext(
                PHOTO_ID_2, MARKER_ID, INCIDENT_ID,
                JPEG_CONTENT_TYPE, OVERSIZED_BYTES,  // 제한 초과
                "FAILED", Instant.now().plusSeconds(600),
                null
        );
    }

    /**
     * FAILED: contentType 불일치 photo context.
     * presign 시 jpeg로 요청했는데 실제로는 다른 타입이 업로드됨.
     */
    public static PhotoContext failedPhoto_contentTypeMismatch() {
        return new PhotoContext(
                PHOTO_ID_2, MARKER_ID, INCIDENT_ID,
                PNG_CONTENT_TYPE, VALID_SIZE_BYTES,  // presign은 JPEG인데 PNG로 업로드
                "FAILED", Instant.now().plusSeconds(600),
                null
        );
    }

    /**
     * Photo lifecycle에서 사용하는 불변 context 객체.
     */
    public record PhotoContext(
            UUID photoId,
            UUID markerId,
            UUID incidentId,
            String contentType,
            long sizeBytes,
            String status,
            Instant presignedExpiresAt,
            Instant finalizedAt
    ) {}
}
