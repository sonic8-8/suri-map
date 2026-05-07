package com.surimap.incident.controller.request;

import jakarta.validation.constraints.NotBlank;

public record ImportIncidentRequest(@NotBlank String sourceIncidentId) {}
