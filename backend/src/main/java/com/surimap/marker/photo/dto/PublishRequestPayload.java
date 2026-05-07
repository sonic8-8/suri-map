package com.surimap.marker.photo.dto;

import java.util.UUID;

public record PublishRequestPayload(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID policePhoneId,
    String status,
    long version,
    PhotoDelta photoDelta) {}
