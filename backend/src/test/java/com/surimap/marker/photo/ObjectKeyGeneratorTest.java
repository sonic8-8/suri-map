package com.surimap.marker.photo;

import com.surimap.marker.photo.fixture.ObjectKeyFixtures;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ObjectKeyGenerator의 MinIO-compatible key 규칙 검증.
 * DB 연결 없이, external S3 호출 없이 실행.
 */
class ObjectKeyGeneratorTest {

    private final ObjectKeyGenerator generator = new ObjectKeyGenerator();

    @Test
    void generateJpegKey() {
        String key = generator.generate(
                ObjectKeyFixtures.INCIDENT_ID,
                ObjectKeyFixtures.MARKER_ID,
                ObjectKeyFixtures.PHOTO_ID,
                "image/jpeg"
        );
        assertThat(key).isEqualTo(ObjectKeyFixtures.EXPECTED_JPEG_KEY);
    }

    @Test
    void generatePngKey() {
        String key = generator.generate(
                ObjectKeyFixtures.INCIDENT_ID,
                ObjectKeyFixtures.MARKER_ID,
                ObjectKeyFixtures.PHOTO_ID,
                "image/png"
        );
        assertThat(key).isEqualTo(ObjectKeyFixtures.EXPECTED_PNG_KEY);
    }

    @Test
    void generateWebpKey() {
        String key = generator.generate(
                ObjectKeyFixtures.INCIDENT_ID,
                ObjectKeyFixtures.MARKER_ID,
                ObjectKeyFixtures.PHOTO_ID,
                "image/webp"
        );
        assertThat(key).isEqualTo(ObjectKeyFixtures.EXPECTED_WEBP_KEY);
    }

    @Test
    void keyStartsWithMarkersPrefix() {
        String key = generator.generate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "image/jpeg"
        );
        assertThat(key).startsWith("markers/");
    }

    @Test
    void keyContainsAllIds() {
        UUID incidentId = UUID.randomUUID();
        UUID markerId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        String key = generator.generate(incidentId, markerId, photoId, "image/jpeg");

        assertThat(key).contains(incidentId.toString());
        assertThat(key).contains(markerId.toString());
        assertThat(key).contains(photoId.toString());
    }

    @Test
    void rejectsUnsupportedContentType() {
        assertThatThrownBy(() -> generator.generate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "image/gif"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported contentType");
    }

    @Test
    void rejectsNullContentType() {
        assertThatThrownBy(() -> generator.generate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullIncidentId() {
        assertThatThrownBy(() -> generator.generate(
                null, UUID.randomUUID(), UUID.randomUUID(),
                "image/jpeg"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void rejectsNullMarkerId() {
        assertThatThrownBy(() -> generator.generate(
                UUID.randomUUID(), null, UUID.randomUUID(),
                "image/jpeg"
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullPhotoId() {
        assertThatThrownBy(() -> generator.generate(
                UUID.randomUUID(), UUID.randomUUID(), null,
                "image/jpeg"
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extensionForReturnsCorrectExtensions() {
        assertThat(generator.extensionFor("image/jpeg")).isEqualTo("jpg");
        assertThat(generator.extensionFor("image/png")).isEqualTo("png");
        assertThat(generator.extensionFor("image/webp")).isEqualTo("webp");
    }

    @Test
    void extensionForRejectsUnsupported() {
        assertThatThrownBy(() -> generator.extensionFor("application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
