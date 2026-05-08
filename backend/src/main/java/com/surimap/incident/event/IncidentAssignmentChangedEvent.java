package com.surimap.incident.event;

import java.util.List;
import java.util.UUID;

/** INCIDENT_ASSIGNMENT_CHANGED payload에 필요한 L1 소유 필드. */
public record IncidentAssignmentChangedEvent(
    UUID id, String status, long version, List<String> changedAccountIds) {

  public IncidentAssignmentChangedEvent {
    changedAccountIds = List.copyOf(changedAccountIds);
  }
}
