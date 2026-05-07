package com.surimap.path;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PathBatchPointRequest(
    String pointId,
    BigDecimal lon,
    BigDecimal lat,
    BigDecimal speedMps,
    Integer horizontalAccuracyM,
    OffsetDateTime clientTs) {}
