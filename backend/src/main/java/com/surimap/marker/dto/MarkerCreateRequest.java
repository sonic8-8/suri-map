package com.surimap.marker.dto;

import java.time.Instant;
import java.util.UUID;

public record MarkerCreateRequest(
    UUID incidentId,
    UUID opId,
    String type,
    MarkerGeoJsonPoint location,
    String supportRequestType,
    String memo,
    Instant clientTs,
    Long clockOffsetMs) {}
