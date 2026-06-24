package com.surimap.app.service.searcharea.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SearchAreaBoundaryAlertServiceRequest(
    UUID incidentId,
    UUID opId,
    UUID searchAreaId,
    String alertType,
    BigDecimal lon,
    BigDecimal lat,
    Instant clientTs,
    UUID searchPathId,
    Integer clockOffsetMs,
    UUID policePhoneId,
    UUID accountId,
    String idempotencyKey) {}
