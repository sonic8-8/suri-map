package com.surimap.incident.lifecycle;

import java.util.Optional;
import java.util.UUID;

/** S1-1 lifecycle guard가 incident 상태를 조회할 때 쓰는 좁은 query 경계. */
public interface IncidentLifecycleQuery {

  Optional<IncidentLifecycleSnapshot> findByIncidentId(UUID incidentId);
}
