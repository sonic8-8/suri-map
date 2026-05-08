package com.surimap.retention.purge;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PurgeRunRepository implements IncidentDataPurgeStore {

  private final PurgeRunMapper mapper;

  public PurgeRunRepository(PurgeRunMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public IncidentDataPurgeRun createIfAbsent(
      UUID incidentId, Instant closedAt, PurgeEnvironmentPolicy environmentPolicy) {
    Instant now = Instant.now();
    Instant purgeDueAt = environmentPolicy.purgeDueAt(closedAt);
    UUID purgeRunId =
        UUID.nameUUIDFromBytes(
            ("incident_data_purge:" + incidentId).getBytes(StandardCharsets.UTF_8));
    mapper.insertIfAbsent(
        purgeRunId,
        incidentId,
        IncidentDataPurgeStatus.PENDING,
        closedAt,
        purgeDueAt,
        environmentPolicy,
        now);
    return mapper
        .findByIncidentId(incidentId)
        .map(PurgeRunRecord::toRun)
        .orElseThrow(() -> new IllegalStateException("incident_data_purge를 찾을 수 없습니다"));
  }

  @Override
  public Optional<IncidentDataPurgeRun> findByIncidentId(UUID incidentId) {
    return mapper.findByIncidentId(incidentId).map(PurgeRunRecord::toRun);
  }

  @Override
  public IncidentDataPurgeRun save(IncidentDataPurgeRun run) {
    mapper.transition(
        run.purgeRunId(),
        run.status(),
        run.lastErrorCode(),
        run.completedAt(),
        run.environmentPolicy(),
        run.version(),
        Instant.now());
    return mapper
        .findByIncidentId(run.incidentId())
        .map(PurgeRunRecord::toRun)
        .orElseThrow(() -> new IllegalStateException("incident_data_purge를 찾을 수 없습니다"));
  }

  @Override
  public List<PurgeHookStepRecord> findHookSteps(UUID purgeRunId) {
    return mapper.findHookSteps(purgeRunId);
  }

  @Override
  public void saveHookStep(
      UUID purgeRunId, PurgeHookName hookName, PurgeHookResult result, Instant now) {
    mapper.upsertHookStep(
        purgeRunId,
        hookName,
        result.status(),
        result.purgedCount(),
        result.retainedCount(),
        result.errorCode(),
        result.status() == PurgeHookStatus.SUCCEEDED ? now : null,
        now);
  }
}
