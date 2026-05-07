package com.surimap.marker.photo.domain;

import java.util.UUID;

public record PhotoMarkerContext(
    UUID incidentId,
    UUID markerId,
    UUID opId,
    UUID policePhoneId,
    String markerStatus,
    long markerVersion) {}
