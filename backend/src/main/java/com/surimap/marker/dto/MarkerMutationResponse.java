package com.surimap.marker.dto;

import java.util.UUID;

public record MarkerMutationResponse(UUID id, String status, long version) {}
