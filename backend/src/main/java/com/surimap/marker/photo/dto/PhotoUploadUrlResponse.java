package com.surimap.marker.photo.dto;

import java.time.Instant;
import java.util.UUID;

public record PhotoUploadUrlResponse(
    UUID photoId, String uploadUrl, Instant expiresAt, long maxSizeBytes, long version) {}
