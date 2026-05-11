package com.surimap.api.controller.operationalperiod.request;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateOperationalPeriodRequest(
    @NotNull UUID incidentId,
    @NotNull String reason,
    String reasonMemo,
    String handoverMemo,
    @NotNull OffsetDateTime clientTs) {}
