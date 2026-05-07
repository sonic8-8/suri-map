package com.surimap.path;

import java.time.OffsetDateTime;

public record PathExcludedPoint(String pointId, String reason, OffsetDateTime clientTs) {}
