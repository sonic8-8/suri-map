package com.surimap.incident.lifecycle;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * S1-1이 제공하는 OPEN/terminal write guard 정책.
 *
 * <p>OPEN만 domain write를 계속 진행시킨다. CLOSED terminal은 재오픈 없이 {@code incident_closed}로 실패하고,
 * OPEN 전 bootstrap 상태는 {@code incident_bootstrapping}으로 실패한다.
 */
@Component
public class IncidentLifecycleGuard {

  private final IncidentLifecycleQuery incidentLifecycleQuery;

  public IncidentLifecycleGuard(IncidentLifecycleQuery incidentLifecycleQuery) {
    this.incidentLifecycleQuery = incidentLifecycleQuery;
  }

  public IncidentLifecycleSnapshot requireOpen(UUID incidentId) {
    IncidentLifecycleSnapshot snapshot =
        incidentLifecycleQuery
            .findByIncidentId(incidentId)
            .orElseThrow(IncidentLifecycleGuardException::incidentBootstrapping);

    if (snapshot.isOpen()) {
      return snapshot;
    }
    if (snapshot.isClosed()) {
      throw IncidentLifecycleGuardException.incidentClosed();
    }
    throw IncidentLifecycleGuardException.incidentBootstrapping();
  }
}
