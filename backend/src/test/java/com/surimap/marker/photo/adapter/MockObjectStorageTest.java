package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.ObjectKeyGenerator;
import com.surimap.marker.photo.fixture.PhotoFixtures;
import com.surimap.marker.photo.port.ObjectStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MockObjectStorage 테스트.
 * DB 연결 없이, external S3/MinIO 호출 없이 실행.
 */
class MockObjectStorageTest {

    private MockObjectStorage storage;
    private ObjectKeyGenerator keyGenerator;

    @BeforeEach
    void setUp() {
        storage = new MockObjectStorage();
        keyGenerator = new ObjectKeyGenerator();
    }

    @Test
    void presignReturnsUploadUrlAndExpiresAt() {
        String objectKey = keyGenerator.generate(
                PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
                PhotoFixtures.PHOTO_ID_1, PhotoFixtures.JPEG_CONTENT_TYPE
        );

        ObjectStoragePort.PresignedUploadResult result = storage.generatePresignedUrl(
                objectKey, PhotoFixtures.JPEG_CONTENT_TYPE,
                PhotoFixtures.VALID_SIZE_BYTES, Duration.ofMinutes(15)
        );

        assertThat(result.uploadUrl()).contains(objectKey);
        assertThat(result.objectKey()).isEqualTo(objectKey);
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    void headObjectReturnsEmptyBeforeUpload() {
        String objectKey = keyGenerator.generate(
                PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
                PhotoFixtures.PHOTO_ID_1, PhotoFixtures.JPEG_CONTENT_TYPE
        );

        storage.generatePresignedUrl(objectKey, PhotoFixtures.JPEG_CONTENT_TYPE,
                PhotoFixtures.VALID_SIZE_BYTES, Duration.ofMinutes(15));

        // presigned만 했고 아직 upload 안 됨
        Optional<ObjectStoragePort.ObjectMetadata> metadata = storage.headObject(objectKey);
        assertThat(metadata).isEmpty();
    }

    @Test
    void headObjectReturnsMetadataAfterUpload() {
        String objectKey = keyGenerator.generate(
                PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
                PhotoFixtures.PHOTO_ID_1, PhotoFixtures.JPEG_CONTENT_TYPE
        );

        storage.generatePresignedUrl(objectKey, PhotoFixtures.JPEG_CONTENT_TYPE,
                PhotoFixtures.VALID_SIZE_BYTES, Duration.ofMinutes(15));
        storage.simulateUpload(objectKey, PhotoFixtures.JPEG_CONTENT_TYPE, PhotoFixtures.VALID_SIZE_BYTES);

        Optional<ObjectStoragePort.ObjectMetadata> metadata = storage.headObject(objectKey);
        assertThat(metadata).isPresent();
        assertThat(metadata.get().objectKey()).isEqualTo(objectKey);
        assertThat(metadata.get().contentType()).isEqualTo(PhotoFixtures.JPEG_CONTENT_TYPE);
        assertThat(metadata.get().sizeBytes()).isEqualTo(PhotoFixtures.VALID_SIZE_BYTES);
    }

    @Test
    void deleteObjectRemovesEntry() {
        String objectKey = keyGenerator.generate(
                PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
                PhotoFixtures.PHOTO_ID_1, PhotoFixtures.JPEG_CONTENT_TYPE
        );

        storage.generatePresignedUrl(objectKey, PhotoFixtures.JPEG_CONTENT_TYPE,
                PhotoFixtures.VALID_SIZE_BYTES, Duration.ofMinutes(15));
        storage.simulateUpload(objectKey, PhotoFixtures.JPEG_CONTENT_TYPE, PhotoFixtures.VALID_SIZE_BYTES);

        storage.deleteObject(objectKey);

        assertThat(storage.headObject(objectKey)).isEmpty();
    }

    @Test
    void deleteNonExistentKeyDoesNotThrow() {
        // 없는 key 삭제해도 에러 없음
        storage.deleteObject("nonexistent/key.jpg");
    }

    @Test
    void simulateExpiryMakesTtlExpired() {
        String objectKey = keyGenerator.generate(
                PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
                PhotoFixtures.PHOTO_ID_1, PhotoFixtures.JPEG_CONTENT_TYPE
        );

        storage.generatePresignedUrl(objectKey, PhotoFixtures.JPEG_CONTENT_TYPE,
                PhotoFixtures.VALID_SIZE_BYTES, Duration.ofMinutes(15));

        assertThat(storage.isExpired(objectKey)).isFalse();

        storage.simulateExpiry(objectKey);

        assertThat(storage.isExpired(objectKey)).isTrue();
    }

    @Test
    void simulateUploadWithDifferentSizeDetectable() {
        String objectKey = keyGenerator.generate(
                PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
                PhotoFixtures.PHOTO_ID_2, PhotoFixtures.JPEG_CONTENT_TYPE
        );

        storage.generatePresignedUrl(objectKey, PhotoFixtures.JPEG_CONTENT_TYPE,
                PhotoFixtures.VALID_SIZE_BYTES, Duration.ofMinutes(15));

        // 실제 업로드는 초과 크기
        storage.simulateUpload(objectKey, PhotoFixtures.JPEG_CONTENT_TYPE, PhotoFixtures.OVERSIZED_BYTES);

        Optional<ObjectStoragePort.ObjectMetadata> metadata = storage.headObject(objectKey);
        assertThat(metadata).isPresent();
        assertThat(metadata.get().sizeBytes()).isEqualTo(PhotoFixtures.OVERSIZED_BYTES);
        // finalize에서 presign 시 요청한 크기와 비교하여 거부할 수 있음
    }

    @Test
    void simulateUploadWithDifferentContentTypeDetectable() {
        String objectKey = keyGenerator.generate(
                PhotoFixtures.INCIDENT_ID, PhotoFixtures.MARKER_ID,
                PhotoFixtures.PHOTO_ID_2, PhotoFixtures.JPEG_CONTENT_TYPE
        );

        storage.generatePresignedUrl(objectKey, PhotoFixtures.JPEG_CONTENT_TYPE,
                PhotoFixtures.VALID_SIZE_BYTES, Duration.ofMinutes(15));

        // JPEG로 presign했는데 PNG로 업로드
        storage.simulateUpload(objectKey, PhotoFixtures.PNG_CONTENT_TYPE, PhotoFixtures.VALID_SIZE_BYTES);

        Optional<ObjectStoragePort.ObjectMetadata> metadata = storage.headObject(objectKey);
        assertThat(metadata).isPresent();
        assertThat(metadata.get().contentType()).isEqualTo(PhotoFixtures.PNG_CONTENT_TYPE);
        // finalize에서 contentType 불일치 감지 가능
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
    void presignRejectsBlankObjectKey() {
        assertThatThrownBy(() -> storage.generatePresignedUrl(
                "", "image/jpeg", 1000, Duration.ofMinutes(15)
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void presignRejectsNegativeTtl() {
        assertThatThrownBy(() -> storage.generatePresignedUrl(
                "markers/test.jpg", "image/jpeg", 1000, Duration.ofSeconds(-1)
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void headObjectReturnsEmptyForNonExistentKey() {
        assertThat(storage.headObject("nonexistent")).isEmpty();
    }

    @Test
    void photoFixturesAreUsableWithMockStorage() {
        // PhotoFixtures의 값들이 mock storage에서 정상 동작하는지 통합 검증
        var photo = PhotoFixtures.presignedPhoto();
        String objectKey = keyGenerator.generate(
                photo.incidentId(), photo.markerId(), photo.photoId(), photo.contentType()
        );

        // presign
        var result = storage.generatePresignedUrl(
                objectKey, photo.contentType(), photo.sizeBytes(), Duration.ofMinutes(15)
        );
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
        String objectKey = keyGenerator.generate(
                photo.incidentId(), photo.markerId(), photo.photoId(), photo.contentType()
        );

        storage.generatePresignedUrl(objectKey, photo.contentType(), photo.sizeBytes(), Duration.ofMinutes(15));
        storage.simulateExpiry(objectKey);

        // TTL 만료 확인
        assertThat(storage.isExpired(objectKey)).isTrue();
        // upload 안 됐으므로 headObject도 empty
        assertThat(storage.headObject(objectKey)).isEmpty();
    }
}
