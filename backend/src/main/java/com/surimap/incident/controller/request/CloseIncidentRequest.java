package com.surimap.incident.controller.request;

import jakarta.validation.constraints.NotBlank;

public record CloseIncidentRequest(
    @NotBlank String closeReason, Boolean confirmPersonalDataRemoval) {}
