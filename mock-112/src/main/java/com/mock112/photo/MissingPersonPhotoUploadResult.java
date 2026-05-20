package com.mock112.photo;

public record MissingPersonPhotoUploadResult(
        String objectKey,
        String photoUrl,
        String storageUri,
        String contentType,
        long sizeBytes) {
}
