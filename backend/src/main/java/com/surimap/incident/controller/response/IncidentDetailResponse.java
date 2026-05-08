package com.surimap.incident.controller.response;

import com.surimap.incident.service.IncidentActiveReadResults.Detail;
import java.util.List;
import java.util.UUID;

/** GET /api/incidents/{incidentId} active 상세 응답. 종료·파기 상태 필드는 후속 task 범위다. */
public record IncidentDetailResponse(
    UUID id,
    UUID incidentId,
    String status,
    long version,
    IncidentDetailMissingPersonSummaryResponse missingPerson,
    List<IncidentAssignmentResponse> assignments) {

  public IncidentDetailResponse {
    assignments = List.copyOf(assignments);
  }

  public static IncidentDetailResponse from(Detail detail) {
    return new IncidentDetailResponse(
        detail.id(),
        detail.incidentId(),
        detail.status(),
        detail.version(),
        IncidentDetailMissingPersonSummaryResponse.from(detail.missingPerson()),
        detail.assignments().stream().map(IncidentAssignmentResponse::from).toList());
  }
}
