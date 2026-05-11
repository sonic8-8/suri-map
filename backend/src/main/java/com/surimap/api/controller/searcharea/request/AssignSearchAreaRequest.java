package com.surimap.api.controller.searcharea.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AssignSearchAreaRequest(
    @NotNull UUID incidentId,
    @NotNull UUID opId,
    @NotNull @Size(min = 1) List<UUID> assigneeAccountIds,
    String memo,
    @NotNull OffsetDateTime clientTs) {}
