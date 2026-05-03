package com.surimap.marker.photo.dto;

import java.util.UUID;

/**
 * 사진 presign 응답 DTO.
 */
public record PhotoPresignResponse(
        UUID photoId,
        String uploadUrl,
        long expiresInSeconds
) {}
