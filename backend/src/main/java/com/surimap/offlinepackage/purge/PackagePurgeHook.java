package com.surimap.offlinepackage.purge;

import com.surimap.retention.purge.PurgeHookResult;
import java.time.Instant;
import java.util.UUID;

/** S7 offline package purge hook consumed by S1-3 retention orchestration. */
@FunctionalInterface
public interface PackagePurgeHook {

  PurgeHookResult purgeIncidentPackage(
      UUID incidentId, UUID purgeRunId, Instant closedAt, Instant purgeDeadlineTs);
}
