package com.surimap.incident.lifecycle;

import com.surimap.incident.domain.IncidentStatus;
import java.util.UUID;

/** write guard가 비교하는 incident lifecycle 최소 상태. */
public record IncidentLifecycleSnapshot(UUID incidentId, String status, long version) {

  public boolean isOpen() {
    return IncidentStatus.OPEN.name().equals(status);
  }

  public boolean isClosed() {
    return IncidentStatus.CLOSED.name().equals(status);
  }
}
