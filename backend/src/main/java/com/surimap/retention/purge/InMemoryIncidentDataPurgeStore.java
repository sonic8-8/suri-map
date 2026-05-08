package com.surimap.retention.purge;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 테스트와 mock contract에서 사용하는 incident_data_purge in-memory 저장소. */
public class InMemoryIncidentDataPurgeStore implements IncidentDataPurgeStore {

  private final Map<UUID, IncidentDataPurgeRun> runsByIncident = new ConcurrentHashMap<>();
  private final Map<UUID, Map<PurgeHookName, PurgeHookStepRecord>> hookSteps =
      new ConcurrentHashMap<>();

  @Override
  public synchronized IncidentDataPurgeRun createIfAbsent(
      UUID incidentId, Instant closedAt, PurgeEnvironmentPolicy environmentPolicy) {
    return runsByIncident.computeIfAbsent(
        incidentId,
        ignored ->
            IncidentDataPurgeRun.pending(
                purgeRunId(incidentId), incidentId, closedAt, environmentPolicy));
  }

  @Override
  public Optional<IncidentDataPurgeRun> findByIncidentId(UUID incidentId) {
    return Optional.ofNullable(runsByIncident.get(incidentId));
  }

  public List<IncidentDataPurgeRun> runsByIncident(UUID incidentId) {
    return findByIncidentId(incidentId).stream().toList();
  }

  @Override
  public synchronized IncidentDataPurgeRun save(IncidentDataPurgeRun run) {
    runsByIncident.put(run.incidentId(), run);
    return run;
  }

  @Override
  public List<PurgeHookStepRecord> findHookSteps(UUID purgeRunId) {
    return List.copyOf(hookSteps.getOrDefault(purgeRunId, Map.of()).values());
  }

  @Override
  public synchronized void saveHookStep(
      UUID purgeRunId, PurgeHookName hookName, PurgeHookResult result, Instant now) {
    Map<PurgeHookName, PurgeHookStepRecord> steps =
        hookSteps.computeIfAbsent(purgeRunId, ignored -> new EnumMap<>(PurgeHookName.class));
    steps.put(
        hookName,
        PurgeHookStepRecord.of(
            purgeRunId,
            hookName,
            result.status(),
            result.purgedCount(),
            result.retainedCount(),
            result.errorCode(),
            result.status() == PurgeHookStatus.SUCCEEDED ? now : null,
            now));
  }

  public List<PurgeHookStepRecord> stepsByPurgeRun(UUID purgeRunId) {
    return new ArrayList<>(hookSteps.getOrDefault(purgeRunId, Map.of()).values());
  }

  private static UUID purgeRunId(UUID incidentId) {
    return UUID.nameUUIDFromBytes(
        ("incident_data_purge:" + incidentId).getBytes(StandardCharsets.UTF_8));
  }
}
