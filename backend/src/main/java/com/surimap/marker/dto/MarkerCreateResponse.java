package com.surimap.marker.dto;

import java.util.UUID;

public record MarkerCreateResponse(
    UUID id, UUID incidentId, UUID opId, UUID policePhoneId, String status, long version) {}
