package com.surimap.marker.service;

import java.util.UUID;

public record MarkerMutationContext(
    UUID incidentId, UUID markerId, UUID opId, UUID policePhoneId) {}
