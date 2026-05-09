package com.surimap.retention.purge;

import com.surimap.incident.event.IncidentClosedEvent;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** S1-3 internal consumer for S1-1 INCIDENT_CLOSED handoff. */
@Component
public class IncidentClosedPurgeHandler {

  private final PurgeCoordinator purgeCoordinator;

  public IncidentClosedPurgeHandler(PurgeCoordinator purgeCoordinator) {
    this.purgeCoordinator = purgeCoordinator;
  }

  public IncidentDataPurgeRun handle(IncidentClosedEvent event) {
    Objects.requireNonNull(event, "event는 null일 수 없습니다");
    return purgeCoordinator.closeIncident(
        event.id(), event.closedAt(), PurgeEnvironmentPolicy.DEMO_24H_SOFT_DELETE);
  }
}
