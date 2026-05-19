package com.surimap.maparea.boundary;

import java.util.UUID;

public record SearchAreaBoundaryAlertContextRow(
    UUID searchAreaId,
    UUID incidentId,
    UUID opId,
    UUID assignedAccountId,
    long searchAreaVersion) {}
