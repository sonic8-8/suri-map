package com.surimap.marker.photo.dto;

import java.util.UUID;

public record PhotoDelta(UUID photoId, String status, long version) {}
