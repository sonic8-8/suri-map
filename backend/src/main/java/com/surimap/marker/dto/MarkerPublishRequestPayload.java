package com.surimap.marker.dto;

import java.time.Instant;
import java.util.UUID;

public record MarkerPublishRequestPayload(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID policePhoneId,
    String status,
    long version,
    String type,
    MarkerGeoJsonPoint location,
    Instant clientTs,
    Instant serverTs) {}
