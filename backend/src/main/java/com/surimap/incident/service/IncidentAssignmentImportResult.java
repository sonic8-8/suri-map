package com.surimap.incident.service;

import java.util.List;
import java.util.UUID;

/** 112/mock assignment polling 반영 결과. */
public record IncidentAssignmentImportResult(
    UUID incidentId, String status, long version, List<String> changedAccountIds) {

  public IncidentAssignmentImportResult {
    changedAccountIds = List.copyOf(changedAccountIds);
  }
}
