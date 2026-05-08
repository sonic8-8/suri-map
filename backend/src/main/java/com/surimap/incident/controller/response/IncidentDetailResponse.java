package com.surimap.incident.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.surimap.incident.service.IncidentActiveReadResults.Detail;
import java.util.List;
import java.util.UUID;

/** GET /api/incidents/{incidentId} 상세 응답. OPEN은 active 필드, CLOSED는 terminal 필드만 JSON에 포함한다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IncidentDetailResponse(
    UUID id,
    UUID incidentId,
    String status,
    long version,
    java.time.Instant closedAt,
    IncidentTerminalSnapshotResponse terminalSnapshot,
    String writeDisabledReason,
    IncidentDetailMissingPersonSummaryResponse missingPerson,
    List<IncidentAssignmentResponse> assignments) {

  public IncidentDetailResponse {
    assignments = assignments == null ? null : List.copyOf(assignments);
  }

  public static IncidentDetailResponse from(Detail detail) {
    return new IncidentDetailResponse(
        detail.id(),
        detail.incidentId(),
        detail.status(),
        detail.version(),
        detail.closedAt(),
        IncidentTerminalSnapshotResponse.from(detail.terminalSnapshot()),
        detail.writeDisabledReason(),
        IncidentDetailMissingPersonSummaryResponse.from(detail.missingPerson()),
        detail.assignments() == null
            ? null
            : detail.assignments().stream().map(IncidentAssignmentResponse::from).toList());
  }
}
