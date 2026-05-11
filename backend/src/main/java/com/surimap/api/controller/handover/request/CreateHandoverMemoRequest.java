package com.surimap.api.controller.handover.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateHandoverMemoRequest(
    @NotNull UUID incidentId,
    @NotNull UUID opId,
    @NotNull String memoTargetType,
    UUID memoTargetId,
    @NotBlank String content,
    @NotNull OffsetDateTime clientTs) {}
