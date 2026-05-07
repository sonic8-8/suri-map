package com.surimap.marker.photo.fixture;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * S5.json photo entity의 상태 전이 시나리오를 위한 fixture factory. DB 없이 도메인 로직과 mock adapter 테스트에서 사용한다.
 *
 * <p>photo 상태 전이: PENDING_UPLOAD → ATTACHED, PENDING_UPLOAD → FAILED
 */
public final class PhotoFixtures {

  /** harness mock object storage/upload URL fixture */
  public static final String MOCK_OBJECT_STORAGE_URI = "mock://object-storage/suri-map-harness";

  public static final String MOCK_UPLOAD_BASE_URL = "http://127.0.0.1:18080/mock-upload";
  public static final String HARNESS_INCIDENT_ID = "inc-precinct-first-001";
  public static final String HARNESS_MARKER_ID = "mk-precinct-clue-001";
  public static final String HARNESS_PHOTO_ID = "photo-precinct-clue-001";
  public static final String HARNESS_UPLOAD_URL = MOCK_UPLOAD_BASE_URL + "/" + HARNESS_PHOTO_ID;
  public static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(15);

  /** 테스트용 고정 UUID */
  public static final UUID INCIDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  public static final UUID MARKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  public static final UUID PHOTO_ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000101");
  public static final UUID PHOTO_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000102");

  /** 기본 사진 metadata */
  public static final String JPEG_CONTENT_TYPE = "image/jpeg";

  public static final String PNG_CONTENT_TYPE = "image/png";
  public static final String WEBP_CONTENT_TYPE = "image/webp";
  public static final String CHECKSUM_SHA256 =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  public static final String CHECKSUM_MISMATCH_SHA256 =
      "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
  public static final String STATUS_PENDING_UPLOAD = "PENDING_UPLOAD";
  public static final String STATUS_ATTACHED = "ATTACHED";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_DELETED = "DELETED";
  public static final long FIXTURE_ONE_MB_BYTES = 1_048_576L;
  public static final long VALID_SIZE_BYTES = 2_000_000L; // 2MB
  public static final long MAX_SIZE_BYTES = 10_485_760L; // 10MB (S5.json 제한)
  public static final long OVERSIZED_BYTES = 10_485_761L; // 10MB + 1
  public static final List<Long> SUCCESS_SIZE_FIXTURES =
      List.of(FIXTURE_ONE_MB_BYTES, MAX_SIZE_BYTES);
  public static final List<Integer> SUCCESS_PHOTO_COUNT_FIXTURES = List.of(0, 1, 10);
  public static final List<String> FAILURE_FIXTURE_KEYS =
      List.of(
          "11장",
          "10_485_761 bytes",
          "upload-url-denied",
          "upload-timeout",
          "upload-500",
          "checksum-mismatch",
          "attach-missing-blob",
          "attach-duplicate");

  private PhotoFixtures() {}

  /** PENDING_UPLOAD 상태의 photo context. upload URL이 발급되었지만 아직 attach되지 않은 상태. */
  public static PhotoContext pendingUploadPhoto() {
    return new PhotoContext(
        PHOTO_ID_1,
        MARKER_ID,
        INCIDENT_ID,
        JPEG_CONTENT_TYPE,
        VALID_SIZE_BYTES,
        STATUS_PENDING_UPLOAD,
        Instant.now().plusSeconds(900), // TTL 15분
        null);
  }

  /** 기존 테스트 호환용 alias. */
  public static PhotoContext presignedPhoto() {
    return pendingUploadPhoto();
  }

  /** upload는 완료됐지만 attach 전인 mock 관찰용 context. */
  public static PhotoContext uploadedPhoto() {
    return pendingUploadPhoto();
  }

  /** ATTACHED 상태의 photo context. 업로드 완료 후 metadata 검증이 통과된 최종 상태. */
  public static PhotoContext attachedPhoto() {
    return new PhotoContext(
        PHOTO_ID_1,
        MARKER_ID,
        INCIDENT_ID,
        JPEG_CONTENT_TYPE,
        VALID_SIZE_BYTES,
        STATUS_ATTACHED,
        null,
        Instant.now());
  }

  /** 기존 테스트 호환용 alias. */
  public static PhotoContext finalizedPhoto() {
    return attachedPhoto();
  }

  /** FAILED: TTL 만료로 실패한 photo context. upload URL이 만료된 후 attach 시도 → 409 write_conflict. */
  public static PhotoContext failedPhoto_ttlExpired() {
    return new PhotoContext(
        PHOTO_ID_1,
        MARKER_ID,
        INCIDENT_ID,
        JPEG_CONTENT_TYPE,
        VALID_SIZE_BYTES,
        STATUS_FAILED,
        Instant.now().minusSeconds(60), // 이미 만료
        null);
  }

  /** FAILED: 크기 불일치 photo context. upload URL 발급 시 요청한 크기와 실제 업로드된 크기가 다름. */
  public static PhotoContext failedPhoto_sizeMismatch() {
    return new PhotoContext(
        PHOTO_ID_2,
        MARKER_ID,
        INCIDENT_ID,
        JPEG_CONTENT_TYPE,
        OVERSIZED_BYTES, // 제한 초과
        STATUS_FAILED,
        Instant.now().plusSeconds(600),
        null);
  }

  /** FAILED: contentType 불일치 photo context. upload URL 발급 시 jpeg로 요청했는데 실제로는 다른 타입이 업로드됨. */
  public static PhotoContext failedPhoto_contentTypeMismatch() {
    return new PhotoContext(
        PHOTO_ID_2,
        MARKER_ID,
        INCIDENT_ID,
        PNG_CONTENT_TYPE,
        VALID_SIZE_BYTES, // upload URL은 JPEG인데 PNG로 업로드
        STATUS_FAILED,
        Instant.now().plusSeconds(600),
        null);
  }

  /** Photo lifecycle에서 사용하는 불변 context 객체. */
  public record PhotoContext(
      UUID photoId,
      UUID markerId,
      UUID incidentId,
      String contentType,
      long sizeBytes,
      String status,
      Instant uploadUrlExpiresAt,
      Instant attachedAt) {}
}
