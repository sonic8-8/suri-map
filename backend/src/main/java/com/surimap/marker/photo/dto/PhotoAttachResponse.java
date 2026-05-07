package com.surimap.marker.photo.dto;

import java.util.UUID;

public record PhotoAttachResponse(
    UUID photoId, String status, long version, UUID markerId, long markerVersion) {}
