package com.surimap.app.service.searcharea;

import java.util.UUID;

public record SearchAreaBoundaryAlertResult(
    UUID id,
    UUID eventId,
    UUID incidentId,
    UUID opId,
    UUID searchAreaId,
    UUID policePhoneId,
    String alertType,
    long version,
    String status) {}
