package com.surimap.incident.controller.response;

import com.surimap.incident.service.IncidentCloseResult;
import java.time.Instant;
import java.util.UUID;

public record CloseIncidentResponse(
    UUID id,
    UUID incidentId,
    String status,
    long version,
    Instant closedAt,
    IncidentTerminalSnapshotResponse terminalSnapshot,
    String writeDisabledReason) {

  public static CloseIncidentResponse from(IncidentCloseResult result) {
    return new CloseIncidentResponse(
        result.id(),
        result.incidentId(),
        result.status(),
        result.version(),
        result.closedAt(),
        IncidentTerminalSnapshotResponse.from(result),
        result.writeDisabledReason());
  }
}
