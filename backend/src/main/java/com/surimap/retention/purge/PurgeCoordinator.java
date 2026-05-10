package com.surimap.retention.purge;

import com.surimap.eventhub.port.EventHub;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S1-3 incident_data_purge 생성, hook 조율, INCIDENT_PURGED 발행 오케스트레이터. */
@Service
public class PurgeCoordinator {

  private final IncidentDataPurgeStore store;
  private final List<PurgeHook> hooks;
  private final EventHub eventHub;
  private final OperationalLogSink operationalLogSink;
  private final Clock clock;

  public PurgeCoordinator(IncidentDataPurgeStore store, List<PurgeHook> hooks, EventHub eventHub) {
    this(store, hooks, eventHub, OperationalLogSink.noop(), Clock.systemUTC());
  }

  @Autowired
  public PurgeCoordinator(
      IncidentDataPurgeStore store,
      List<PurgeHook> hooks,
      EventHub eventHub,
      OperationalLogSink operationalLogSink,
      Clock clock) {
    this.store = Objects.requireNonNull(store, "store는 null일 수 없습니다");
    this.hooks = List.copyOf(Objects.requireNonNull(hooks, "hooks는 null일 수 없습니다"));
    this.eventHub = Objects.requireNonNull(eventHub, "eventHub는 null일 수 없습니다");
    this.operationalLogSink =
        Objects.requireNonNull(operationalLogSink, "operationalLogSink는 null일 수 없습니다");
    this.clock = Objects.requireNonNull(clock, "clock은 null일 수 없습니다");
  }

  @Transactional
  public IncidentDataPurgeRun closeIncident(
      UUID incidentId, Instant closedAt, PurgeEnvironmentPolicy environmentPolicy) {
    IncidentDataPurgeRun run = store.createIfAbsent(incidentId, closedAt, environmentPolicy);
    operationalLogSink.append(
        new OperationalLogEntry(
            "incident_data_purge.close_observed",
            incidentId,
            run.purgeRunId(),
            clock.instant(),
            Map.of("status", run.status().name())));
    return run;
  }

  @Transactional
  public IncidentDataPurgeRun purgeIncident(UUID incidentId, UUID purgeRunId) {
    IncidentDataPurgeRun run =
        store
            .findByIncidentId(incidentId)
            .filter(candidate -> candidate.purgeRunId().equals(purgeRunId))
            .orElseThrow(() -> new IllegalStateException("incident_data_purge를 찾을 수 없습니다"));
    if (run.status() == IncidentDataPurgeStatus.COMPLETED) {
      return run;
    }

    Instant now = clock.instant();
    run =
        store.save(
            run.transition(IncidentDataPurgeStatus.RUNNING, null, null, run.localPurgeState()));

    PurgeHookRequest request =
        new PurgeHookRequest(
            run.incidentId(), run.purgeRunId(), run.closedAt(), run.purgeDeadlineTs());
    for (PurgeHook hook : hooks) {
      if (isSucceeded(run.purgeRunId(), hook.name())) {
        continue;
      }

      PurgeHookResult result = hook.purge(request);
      store.saveHookStep(run.purgeRunId(), hook.name(), result, now);
      if (result.status() == PurgeHookStatus.WAITING_FOR_SYNC) {
        return store.save(
            run.transition(
                IncidentDataPurgeStatus.WAITING_FOR_SYNC,
                null,
                result.errorCode(),
                LocalPurgeState.WAITING_FOR_SYNC));
      }
      if (result.status() == PurgeHookStatus.FAILED_RETRYABLE) {
        return store.save(
            run.transition(
                IncidentDataPurgeStatus.FAILED_RETRYABLE,
                null,
                result.errorCode(),
                LocalPurgeState.FAILED_RETRYABLE));
      }
    }

    IncidentDataPurgeRun completed =
        store.save(
            run.transition(
                IncidentDataPurgeStatus.COMPLETED, now, null, LocalPurgeState.LOCAL_PURGED));
    eventHub.publish(
        new IncidentPurgedPublishRequest(
                completed.incidentId(),
                completed.version(),
                completed.purgeRunId(),
                Objects.requireNonNullElse(completed.completedAt(), clock.instant()))
            .toPublishRequest());
    operationalLogSink.append(
        new OperationalLogEntry(
            "incident_data_purge.completed",
            completed.incidentId(),
            completed.purgeRunId(),
            clock.instant(),
            Map.of("version", completed.version())));
    return completed;
  }

  private boolean isSucceeded(UUID purgeRunId, PurgeHookName hookName) {
    return store.findHookSteps(purgeRunId).stream()
        .filter(step -> step.getHookName() == hookName)
        .max(Comparator.comparing(PurgeHookStepRecord::getUpdatedAt))
        .map(step -> step.getStatus() == PurgeHookStatus.SUCCEEDED)
        .orElse(false);
  }
}
