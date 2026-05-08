package com.surimap.incident.controller.response;

import com.surimap.incident.service.IncidentActiveReadResults.TerminalSnapshot;
import com.surimap.incident.service.IncidentCloseResult;
import java.time.Instant;
import java.util.UUID;

/** close 응답에 포함되는 sanitized terminal snapshot. 실종자 PII 필드는 의도적으로 두지 않는다. */
public record IncidentTerminalSnapshotResponse(
    UUID id,
    UUID incidentId,
    String status,
    long version,
    Instant closedAt,
    String writeDisabledReason) {

  public static IncidentTerminalSnapshotResponse from(IncidentCloseResult result) {
    return new IncidentTerminalSnapshotResponse(
        result.id(),
        result.incidentId(),
        result.status(),
        result.version(),
        result.closedAt(),
        result.writeDisabledReason());
  }

  public static IncidentTerminalSnapshotResponse from(TerminalSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    return new IncidentTerminalSnapshotResponse(
        snapshot.id(),
        snapshot.incidentId(),
        snapshot.status(),
        snapshot.version(),
        snapshot.closedAt(),
        snapshot.writeDisabledReason());
  }
}
