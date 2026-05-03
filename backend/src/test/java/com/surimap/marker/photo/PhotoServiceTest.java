package com.surimap.marker.photo;

import com.surimap.marker.photo.adapter.MockObjectStorageAdapter;
import com.surimap.marker.photo.dto.PhotoFinalizeResponse;
import com.surimap.marker.photo.dto.PhotoPresignRequest;
import com.surimap.marker.photo.dto.PhotoPresignResponse;
import com.surimap.marker.photo.service.PhotoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 사진 presign/finalize 테스트.
 *
 * SC-06 harness 기준:
 * - presign 성공 시 photoId + uploadUrl 반환
 * - finalize 성공 시 status=ACTIVE
 * - 미업로드 finalize 거부
 * - 10장/10MB 초과 거부
 */
@DisplayName("사진 presign/finalize 테스트")
class PhotoServiceTest {

    private MockObjectStorageAdapter storage;
    private PhotoService photoService;

    private static final UUID MARKER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        storage = new MockObjectStorageAdapter();
        photoService = new PhotoService(storage);
    }

    // ══════════════════════════════════════════════════════
    // Presign
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("presign")
    class Presign {

        @Test
        @DisplayName("정상 요청 → photoId + uploadUrl 반환")
        void validRequest_returnsPresignedUrl() {
            PhotoPresignRequest request = new PhotoPresignRequest(
                    MARKER_ID, "image/jpeg", 1_048_576L);

            PhotoPresignResponse response = photoService.presign(MARKER_ID, request);

            assertThat(response.photoId()).isNotNull();
            assertThat(response.uploadUrl()).startsWith("http://127.0.0.1:18080/mock-upload/");
            assertThat(response.expiresInSeconds()).isEqualTo(3600L);
        }

        @Test
        @DisplayName("10MB 초과 → 거부")
        void oversizedFile_rejected() {
            PhotoPresignRequest request = new PhotoPresignRequest(
                    MARKER_ID, "image/jpeg", 10_485_761L);

            assertThatThrownBy(() -> photoService.presign(MARKER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("지원하지 않는 MIME type → 거부")
        void unsupportedMimeType_rejected() {
            PhotoPresignRequest request = new PhotoPresignRequest(
                    MARKER_ID, "application/pdf", 1_048_576L);

            assertThatThrownBy(() -> photoService.presign(MARKER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("파일 크기 0 → 거부")
        void zeroFileSize_rejected() {
            PhotoPresignRequest request = new PhotoPresignRequest(
                    MARKER_ID, "image/jpeg", 0L);

            assertThatThrownBy(() -> photoService.presign(MARKER_ID, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ══════════════════════════════════════════════════════
    // Finalize
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("finalize")
    class Finalize {

        @Test
        @DisplayName("업로드 완료 후 finalize → ACTIVE")
        void uploadedPhoto_finalize_active() {
            // presign
            PhotoPresignRequest request = new PhotoPresignRequest(
                    MARKER_ID, "image/jpeg", 1_048_576L);
            PhotoPresignResponse presigned = photoService.presign(MARKER_ID, request);

            // 업로드 시뮬레이션
            storage.simulateUpload(presigned.photoId());

            // finalize
            PhotoFinalizeResponse response = photoService.finalize(MARKER_ID, presigned.photoId());

            assertThat(response.status()).isEqualTo("ACTIVE");
            assertThat(response.photoId()).isEqualTo(presigned.photoId());
            assertThat(response.markerId()).isEqualTo(MARKER_ID);
        }

        @Test
        @DisplayName("미업로드 finalize → finalize_missing_blob")
        void notUploaded_finalize_rejected() {
            UUID fakePhotoId = UUID.randomUUID();

            assertThatThrownBy(() -> photoService.finalize(MARKER_ID, fakePhotoId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("finalize_missing_blob");
        }
    }
}
