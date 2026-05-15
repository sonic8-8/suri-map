package com.surimap.marker.photo.dto;

import java.util.UUID;

public record MarkerCreatePhotoUploadUrlRequest(
    UUID markerId,
    UUID incidentId,
    UUID opId,
    String contentType,
    long sizeBytes,
    String checksumSha256) {}
