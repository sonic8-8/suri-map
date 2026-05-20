package com.mock112.photo;

import java.io.IOException;
import java.io.InputStream;

public interface MissingPersonPhotoStorage {

    MissingPersonPhotoUploadResult put(
            String objectKey,
            String contentType,
            long sizeBytes,
            InputStream inputStream) throws IOException;
}
