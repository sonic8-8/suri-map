package com.surimap.path;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SearchPathPoint(
    String pointId,
    OffsetDateTime clientTs,
    BigDecimal lon,
    BigDecimal lat,
    BigDecimal speedMps,
    Integer horizontalAccuracyM) {}
