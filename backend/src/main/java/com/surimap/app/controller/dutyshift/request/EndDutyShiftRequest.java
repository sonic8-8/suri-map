package com.surimap.app.controller.dutyshift.request;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EndDutyShiftRequest(
    @NotNull UUID incidentId,
    @NotNull UUID opId,
    @NotNull String action,
    String memo,
    @NotNull OffsetDateTime clientTs) {}
