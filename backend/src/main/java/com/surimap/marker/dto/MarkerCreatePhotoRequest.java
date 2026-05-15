package com.surimap.marker.dto;

import java.util.UUID;

public record MarkerCreatePhotoRequest(
    UUID photoId,
    long sizeBytes,
    String contentType,
    Integer width,
    Integer height,
    String checksumSha256) {}
