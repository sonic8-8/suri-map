package com.surimap.incident.controller.response;

import com.surimap.incident.service.IncidentActiveReadResults.Assignment;
import java.time.Instant;

/** 사건 상세의 active incident_assignment 요약. */
public record IncidentAssignmentResponse(
    String accountId,
    String accountDisplayName,
    String accountType,
    String organizationType,
    String incidentRole,
    Instant assignedAt) {

  public static IncidentAssignmentResponse from(Assignment assignment) {
    return new IncidentAssignmentResponse(
        assignment.accountId(),
        assignment.accountDisplayName(),
        assignment.accountType(),
        assignment.organizationType(),
        assignment.incidentRole(),
        assignment.assignedAt());
  }
}
