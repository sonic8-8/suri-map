package com.surimap.incident.controller.response;

import com.surimap.incident.service.IncidentActiveReadResults.Assignment;

/** 사건 상세의 active incident_assignment 요약. 계정 식별자와 사건 안 역할만 노출한다. */
public record IncidentAssignmentResponse(String accountId, String incidentRole) {

  public static IncidentAssignmentResponse from(Assignment assignment) {
    return new IncidentAssignmentResponse(assignment.accountId(), assignment.incidentRole());
  }
}
