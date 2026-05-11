package com.surimap.app.controller.dutyshift.request;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StartDutyShiftRequest(
    @NotNull UUID incidentId,
    @NotNull UUID opId,
    @NotNull UUID policePhoneId,
    @NotNull OffsetDateTime clientTs) {}
