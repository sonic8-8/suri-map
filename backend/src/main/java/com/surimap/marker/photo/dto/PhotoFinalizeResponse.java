package com.surimap.marker.photo.dto;

import java.util.UUID;

/**
 * 사진 finalize 응답 DTO.
 */
public record PhotoFinalizeResponse(
        UUID photoId,
        UUID markerId,
        String status,
        long version
) {}
