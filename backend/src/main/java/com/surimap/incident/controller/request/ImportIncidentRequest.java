package com.surimap.incident.controller.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ImportIncidentRequest(@NotNull UUID sourceIncidentId) {}
