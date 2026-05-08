package com.surimap.retention.purge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentDataPurgeStore {

  IncidentDataPurgeRun createIfAbsent(
      UUID incidentId, Instant closedAt, PurgeEnvironmentPolicy environmentPolicy);

  Optional<IncidentDataPurgeRun> findByIncidentId(UUID incidentId);

  IncidentDataPurgeRun save(IncidentDataPurgeRun run);

  List<PurgeHookStepRecord> findHookSteps(UUID purgeRunId);

  void saveHookStep(UUID purgeRunId, PurgeHookName hookName, PurgeHookResult result, Instant now);
}
