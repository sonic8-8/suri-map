package com.surimap.marker.photo.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.marker.photo.ObjectKeyGenerator;
import com.surimap.marker.photo.fixture.ObjectKeyFixtures;
import com.surimap.marker.photo.fixture.PhotoFixtures;
import com.surimap.marker.photo.port.ObjectStoragePort;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** MockObjectStorage 테스트. DB 연결 없이, external S3/MinIO 호출 없이 실행. */
class MockObjectStorageTest {

  private MockObjectStorage storage;
  private ObjectKeyGenerator keyGenerator;

  @BeforeEach
  void setUp() {
    storage = new MockObjectStorage();
    keyGenerator = new ObjectKeyGenerator();
  }

  @Test
  void uploadUrlUsesHarnessEndpointAndPhotoId() {
    Instant lowerBound = Instant.now().plus(PhotoFixtures.UPLOAD_URL_TTL).minusSeconds(1);
    ObjectStoragePort.PresignedUploadResult result =
        storage.generatePresignedUrl(
            ObjectKeyFixtures.EXPECTED_HARNESS_JPEG_KEY,
            PhotoFixtures.JPEG_CONTENT_TYPE,
            PhotoFixtures.FIXTURE_ONE_MB_BYTES,
            PhotoFixtures.UPLOAD_URL_TTL);
    Instant upperBound = Instant.now().plus(PhotoFixtures.UPLOAD_URL_TTL).plusSeconds(1);

    assertThat(result.uploadUrl()).isEqualTo(PhotoFixtures.HARNESS_UPLOAD_URL);
    assertThat(result.objectKey()).isEqualTo(ObjectKeyFixtures.EXPECTED_HARNESS_JPEG_KEY);
    assertThat(result.storageUri()).isEqualTo(PhotoFixtures.MOCK_OBJECT_STORAGE_URI);
    assertThat(result.contentType()).isEqualTo(PhotoFixtures.JPEG_CONTENT_TYPE);
    assertThat(result.maxSizeBytes()).isEqualTo(PhotoFixtures.FIXTURE_ONE_MB_BYTES);
    assertThat(result.expiresAt()).isBetween(lowerBound, upperBound);
  }

  @Test
  void metadataRecordIncludesChecksumFixtureField() {
    assertThat(
            Arrays.stream(ObjectStoragePort.ObjectMetadata.class.getRecordComponents())
                .map(component -> component.getName()))
        .contains("checksumSha256");
  }

  @Test
  void photoFixturePinsHarnessLimitsStatusesAndFailureKeys() {
    assertThat(PhotoFixtures.MOCK_OBJECT_STORAGE_URI)
        .isEqualTo("mock://object-storage/suri-map-harness");
    assertThat(PhotoFixtures.SUCCESS_PHOTO_COUNT_FIXTURES).containsExactly(0, 1, 10);
    assertThat(PhotoFixtures.SUCCESS_SIZE_FIXTURES).containsExactly(1_048_576L, 10_485_760L);
    assertThat(PhotoFixtures.UPLOAD_URL_TTL).isEqualTo(Duration.ofMinutes(15));
    assertThat(PhotoFixtures.STATUS_PENDING_UPLOAD).isEqualTo("PENDING_UPLOAD");
    assertThat(PhotoFixtures.STATUS_ATTACHED).isEqualTo("ATTACHED");
    assertThat(PhotoFixtures.FAILURE_FIXTURE_KEYS)
        .containsExactly(
            "11장",
            "10_485_761 bytes",
            "upload-url-denied",
            "upload-timeout",
            "upload-500",
            "checksum-mismatch",
            "attach-missing-blob",
            "attach-duplicate");
  }

  @Test
  void uploadUrlReturnsCanonicalUploadUrlAndExpiresAt() {
    String objectKey =
        keyGenerator.generate(
            PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
            PhotoFixtures.PHOTO_ID_1, PhotoFixtures.JPEG_CONTENT_TYPE);

    ObjectStoragePort.PresignedUploadResult result =
        storage.generatePresignedUrl(
            objectKey,
            PhotoFixtures.JPEG_CONTENT_TYPE,
            PhotoFixtures.VALID_SIZE_BYTES,
            Duration.ofMinutes(15));

    assertThat(result.uploadUrl())
        .isEqualTo(PhotoFixtures.MOCK_UPLOAD_BASE_URL + "/" + PhotoFixtures.PHOTO_ID_1);
    assertThat(result.objectKey()).isEqualTo(objectKey);
    assertThat(result.expiresAt()).isNotNull();
  }

  @Test
  void headObjectReturnsEmptyBeforeUpload() {
    String objectKey =
        keyGenerator.generate(
            PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
            PhotoFixtures.PHOTO_ID_1, PhotoFixtures.JPEG_CONTENT_TYPE);

    storage.generatePresignedUrl(
        objectKey,
        PhotoFixtures.JPEG_CONTENT_TYPE,
        PhotoFixtures.VALID_SIZE_BYTES,
        Duration.ofMinutes(15));

    // upload URL만 발급했고 아직 upload 안 됨
    Optional<ObjectStoragePort.ObjectMetadata> metadata = storage.headObject(objectKey);
    assertThat(metadata).isEmpty();
  }

  @Test
  void headObjectReturnsMetadataAfterUpload() {
    String objectKey =
        keyGenerator.generate(
            PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
            PhotoFixtures.PHOTO_ID_1, PhotoFixtures.JPEG_CONTENT_TYPE);

    storage.generatePresignedUrl(
        objectKey,
        PhotoFixtures.JPEG_CONTENT_TYPE,
        PhotoFixtures.VALID_SIZE_BYTES,
        Duration.ofMinutes(15));
    storage.simulateUpload(
        objectKey, PhotoFixtures.JPEG_CONTENT_TYPE, PhotoFixtures.VALID_SIZE_BYTES);

    Optional<ObjectStoragePort.ObjectMetadata> metadata = storage.headObject(objectKey);
    assertThat(metadata).isPresent();
    assertThat(metadata.get().objectKey()).isEqualTo(objectKey);
    assertThat(metadata.get().contentType()).isEqualTo(PhotoFixtures.JPEG_CONTENT_TYPE);
    assertThat(metadata.get().sizeBytes()).isEqualTo(PhotoFixtures.VALID_SIZE_BYTES);
  }

  @Test
  void headObjectReturnsChecksumMetadataAfterUpload() {
    String objectKey = ObjectKeyFixtures.EXPECTED_HARNESS_JPEG_KEY;
    storage.generatePresignedUrl(
        objectKey,
        PhotoFixtures.JPEG_CONTENT_TYPE,
        PhotoFixtures.FIXTURE_ONE_MB_BYTES,
        PhotoFixtures.CHECKSUM_SHA256,
        Duration.ofMinutes(15));

    storage.simulateUpload(
        objectKey,
        PhotoFixtures.JPEG_CONTENT_TYPE,
        PhotoFixtures.FIXTURE_ONE_MB_BYTES,
        PhotoFixtures.CHECKSUM_SHA256);

    Optional<ObjectStoragePort.ObjectMetadata> metadata = storage.headObject(objectKey);
    assertThat(metadata).isPresent();
    assertThat(metadata.get().checksumSha256()).isEqualTo(PhotoFixtures.CHECKSUM_SHA256);
  }

  @Test
  void checksumMismatchIsDetectableFromMetadata() {
    String objectKey = ObjectKeyFixtures.EXPECTED_HARNESS_JPEG_KEY;
    storage.generatePresignedUrl(
        objectKey,
        PhotoFixtures.JPEG_CONTENT_TYPE,
        PhotoFixtures.FIXTURE_ONE_MB_BYTES,
        PhotoFixtures.CHECKSUM_SHA256,
        Duration.ofMinutes(15));

    storage.simulateUpload(
        objectKey,
        PhotoFixtures.JPEG_CONTENT_TYPE,
        PhotoFixtures.FIXTURE_ONE_MB_BYTES,
        PhotoFixtures.CHECKSUM_MISMATCH_SHA256);

    Optional<ObjectStoragePort.ObjectMetadata> metadata = storage.headObject(objectKey);
    assertThat(metadata).isPresent();
    assertThat(metadata.get().checksumSha256()).isEqualTo(PhotoFixtures.CHECKSUM_MISMATCH_SHA256);
    assertThat(metadata.get().checksumSha256()).isNotEqualTo(PhotoFixtures.CHECKSUM_SHA256);
  }

  @Test
  void deleteObjectRemovesEntry() {
    String objectKey =
        keyGenerator.generate(
            PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
            PhotoFixtures.PHOTO_ID_1, PhotoFixtures.JPEG_CONTENT_TYPE);

    storage.generatePresignedUrl(
        objectKey,
        PhotoFixtures.JPEG_CONTENT_TYPE,
        PhotoFixtures.VALID_SIZE_BYTES,
        Duration.ofMinutes(15));
    storage.simulateUpload(
        objectKey, PhotoFixtures.JPEG_CONTENT_TYPE, PhotoFixtures.VALID_SIZE_BYTES);

    storage.deleteObject(objectKey);

    assertThat(storage.headObject(objectKey)).isEmpty();
  }

  @Test
  void deleteNonExistentKeyDoesNotThrow() {
    // 없는 key 삭제해도 에러 없음
    storage.deleteObject("nonexistent/key.jpg");
  }

  @Test
  void uploadUrlDeniedFailureFixtureIsInjectable() {
    storage.failNextUploadUrl("upload-url-denied");

    assertThatThrownBy(
            () ->
                storage.generatePresignedUrl(
                    ObjectKeyFixtures.EXPECTED_HARNESS_JPEG_KEY,
                    PhotoFixtures.JPEG_CONTENT_TYPE,
                    PhotoFixtures.FIXTURE_ONE_MB_BYTES,
                    PhotoFixtures.UPLOAD_URL_TTL))
        .isInstanceOf(MockObjectStorage.FixtureFailureException.class)
        .hasMessageContaining("upload-url-denied");
  }

  @Test
  void uploadAndAttachFailureFixturesAreRecordedByObjectKey() {
    storage.generatePresignedUrl(
        ObjectKeyFixtures.EXPECTED_HARNESS_JPEG_KEY,
        PhotoFixtures.JPEG_CONTENT_TYPE,
        PhotoFixtures.FIXTURE_ONE_MB_BYTES,
        PhotoFixtures.UPLOAD_URL_TTL);
    storage.simulateFailure(ObjectKeyFixtures.EXPECTED_HARNESS_JPEG_KEY, "upload-timeout");

    assertThat(storage.failureFixtureFor(ObjectKeyFixtures.EXPECTED_HARNESS_JPEG_KEY))
        .contains("upload-timeout");
    assertThat(storage.headObject(ObjectKeyFixtures.EXPECTED_HARNESS_JPEG_KEY)).isEmpty();
  }

  @Test
  void simulateExpiryMakesTtlExpired() {
    String objectKey =
        keyGenerator.generate(
            PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
            PhotoFixtures.PHOTO_ID_1, PhotoFixtures.JPEG_CONTENT_TYPE);

    storage.generatePresignedUrl(
        objectKey,
        PhotoFixtures.JPEG_CONTENT_TYPE,
        PhotoFixtures.VALID_SIZE_BYTES,
        Duration.ofMinutes(15));

    assertThat(storage.isExpired(objectKey)).isFalse();

    storage.simulateExpiry(objectKey);

    assertThat(storage.isExpired(objectKey)).isTrue();
  }

  @Test
  void simulateUploadWithDifferentSizeDetectable() {
    String objectKey =
        keyGenerator.generate(
            PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
            PhotoFixtures.PHOTO_ID_2, PhotoFixtures.JPEG_CONTENT_TYPE);

    storage.generatePresignedUrl(
        objectKey,
        PhotoFixtures.JPEG_CONTENT_TYPE,
        PhotoFixtures.VALID_SIZE_BYTES,
        Duration.ofMinutes(15));

    // 실제 업로드는 초과 크기
    storage.simulateUpload(
        objectKey, PhotoFixtures.JPEG_CONTENT_TYPE, PhotoFixtures.OVERSIZED_BYTES);

    Optional<ObjectStoragePort.ObjectMetadata> metadata = storage.headObject(objectKey);
    assertThat(metadata).isPresent();
    assertThat(metadata.get().sizeBytes()).isEqualTo(PhotoFixtures.OVERSIZED_BYTES);
    // attach에서 upload URL 발급 시 요청한 크기와 비교하여 거부할 수 있음
  }

  @Test
  void simulateUploadWithDifferentContentTypeDetectable() {
    String objectKey =
        keyGenerator.generate(
            PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
            PhotoFixtures.PHOTO_ID_2, PhotoFixtures.JPEG_CONTENT_TYPE);

    storage.generatePresignedUrl(
        objectKey,
        PhotoFixtures.JPEG_CONTENT_TYPE,
        PhotoFixtures.VALID_SIZE_BYTES,
        Duration.ofMinutes(15));

    // JPEG upload URL을 발급했는데 PNG로 업로드
    storage.simulateUpload(
        objectKey, PhotoFixtures.PNG_CONTENT_TYPE, PhotoFixtures.VALID_SIZE_BYTES);

    Optional<ObjectStoragePort.ObjectMetadata> metadata = storage.headObject(objectKey);
    assertThat(metadata).isPresent();
    assertThat(metadata.get().contentType()).isEqualTo(PhotoFixtures.PNG_CONTENT_TYPE);
    // attach에서 contentType 불일치 감지 가능
  }

  @Test
  void clearRemovesAllEntries() {
    String key1 = "markers/test1.jpg";
    String key2 = "markers/test2.jpg";

    storage.generatePresignedUrl(key1, "image/jpeg", 1000, Duration.ofMinutes(15));
    storage.generatePresignedUrl(key2, "image/jpeg", 1000, Duration.ofMinutes(15));

    assertThat(storage.size()).isEqualTo(2);

    storage.clear();

    assertThat(storage.size()).isEqualTo(0);
  }

  @Test
  void uploadUrlRejectsBlankObjectKey() {
    assertThatThrownBy(
            () -> storage.generatePresignedUrl("", "image/jpeg", 1000, Duration.ofMinutes(15)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void uploadUrlRejectsNegativeTtl() {
    assertThatThrownBy(
            () ->
                storage.generatePresignedUrl(
                    "markers/test.jpg", "image/jpeg", 1000, Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void uploadUrlRejectsOversizedFixtureSize() {
    assertThatThrownBy(
            () ->
                storage.generatePresignedUrl(
                    ObjectKeyFixtures.EXPECTED_HARNESS_JPEG_KEY,
                    PhotoFixtures.JPEG_CONTENT_TYPE,
                    PhotoFixtures.OVERSIZED_BYTES,
                    PhotoFixtures.UPLOAD_URL_TTL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sizeBytes");
  }

  @Test
  void uploadUrlRejectsUnsupportedContentType() {
    assertThatThrownBy(
            () ->
                storage.generatePresignedUrl(
                    ObjectKeyFixtures.EXPECTED_HARNESS_JPEG_KEY,
                    "image/gif",
                    PhotoFixtures.FIXTURE_ONE_MB_BYTES,
                    PhotoFixtures.UPLOAD_URL_TTL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contentType");
  }

  @Test
  void uploadUrlRejectsInvalidChecksumFixture() {
    assertThatThrownBy(
            () ->
                storage.generatePresignedUrl(
                    ObjectKeyFixtures.EXPECTED_HARNESS_JPEG_KEY,
                    PhotoFixtures.JPEG_CONTENT_TYPE,
                    PhotoFixtures.FIXTURE_ONE_MB_BYTES,
                    "not-a-sha256",
                    PhotoFixtures.UPLOAD_URL_TTL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("checksumSha256");
  }

  @Test
  void headObjectReturnsEmptyForNonExistentKey() {
    assertThat(storage.headObject("nonexistent")).isEmpty();
  }

  @Test
  void photoFixturesAreUsableWithMockStorage() {
    // PhotoFixtures의 값들이 mock storage에서 정상 동작하는지 통합 검증
    var photo = PhotoFixtures.pendingUploadPhoto();
    String objectKey =
        keyGenerator.generate(
            photo.incidentId(), photo.markerId(), photo.photoId(), photo.contentType());

    // upload URL 발급
    var result =
        storage.generatePresignedUrl(
            objectKey, photo.contentType(), photo.sizeBytes(), Duration.ofMinutes(15));
    assertThat(result.uploadUrl()).isNotBlank();

    // upload
    storage.simulateUpload(objectKey, photo.contentType(), photo.sizeBytes());

    // head → 확인
    var metadata = storage.headObject(objectKey);
    assertThat(metadata).isPresent();
    assertThat(metadata.get().sizeBytes()).isEqualTo(photo.sizeBytes());
  }

  @Test
  void failedPhotoFixture_ttlExpiredScenario() {
    var photo = PhotoFixtures.failedPhoto_ttlExpired();
    String objectKey =
        keyGenerator.generate(
            photo.incidentId(), photo.markerId(), photo.photoId(), photo.contentType());

    storage.generatePresignedUrl(
        objectKey, photo.contentType(), photo.sizeBytes(), Duration.ofMinutes(15));
    storage.simulateExpiry(objectKey);

    // TTL 만료 확인
    assertThat(storage.isExpired(objectKey)).isTrue();
    // upload 안 됐으므로 headObject도 empty
    assertThat(storage.headObject(objectKey)).isEmpty();
  }
}
