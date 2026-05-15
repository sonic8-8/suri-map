package com.surimap.marker.dto;

import java.util.UUID;

public record MarkerCreatePhotoResponse(
    UUID photoId, String status, long version, UUID markerId, long markerVersion) {}
