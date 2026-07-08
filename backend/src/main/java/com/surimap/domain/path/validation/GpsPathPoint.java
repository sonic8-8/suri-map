package com.surimap.domain.path.validation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record GpsPathPoint(
    String pointId,
    OffsetDateTime clientTs,
    BigDecimal lon,
    BigDecimal lat,
    BigDecimal speedMps,
    Integer horizontalAccuracyM) {}
