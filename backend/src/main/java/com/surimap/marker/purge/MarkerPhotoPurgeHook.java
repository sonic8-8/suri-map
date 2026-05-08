package com.surimap.marker.purge;

import com.surimap.retention.purge.PurgeHookResult;
import java.time.Instant;
import java.util.UUID;

/** S5 marker/photo purge hook consumed by S1-3 retention orchestration. */
@FunctionalInterface
public interface MarkerPhotoPurgeHook {

  PurgeHookResult purgeIncidentMarkerPhotos(
      UUID incidentId, UUID purgeRunId, Instant closedAt, Instant purgeDeadlineTs);
}
