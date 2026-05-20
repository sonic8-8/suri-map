package com.mock112.photo;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "mock112.object-storage.provider",
        havingValue = "mock",
        matchIfMissing = true)
public class MockMissingPersonPhotoStorage implements MissingPersonPhotoStorage {

    @Override
    public MissingPersonPhotoUploadResult put(
            String objectKey,
            String contentType,
            long sizeBytes,
            InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        while (inputStream.read(buffer) != -1) {
            // Drain the mock upload stream so tests exercise the same I/O path.
        }
        return new MissingPersonPhotoUploadResult(
                objectKey,
                "/mock-upload/" + objectKey,
                "mock://object-storage/suri-map-harness",
                contentType,
                sizeBytes);
    }
}
