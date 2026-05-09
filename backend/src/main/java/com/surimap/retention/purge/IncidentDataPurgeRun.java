package com.surimap.retention.purge;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** S1-3 internal/system-only incident_data_purge 상태 projection. */
public record IncidentDataPurgeRun(
    UUID purgeRunId,
    UUID incidentId,
    IncidentDataPurgeStatus status,
    Instant closedAt,
    Instant purgeDeadlineTs,
    Instant completedAt,
    String lastErrorCode,
    long version,
    PurgeEnvironmentPolicy environmentPolicy,
    LocalPurgeState localPurgeState) {

  public IncidentDataPurgeRun {
    Objects.requireNonNull(purgeRunId, "purgeRunId는 null일 수 없습니다");
    Objects.requireNonNull(incidentId, "incidentId는 null일 수 없습니다");
    Objects.requireNonNull(status, "status는 null일 수 없습니다");
    Objects.requireNonNull(closedAt, "closedAt은 null일 수 없습니다");
    Objects.requireNonNull(purgeDeadlineTs, "purgeDeadlineTs는 null일 수 없습니다");
    Objects.requireNonNull(environmentPolicy, "environmentPolicy는 null일 수 없습니다");
    Objects.requireNonNull(localPurgeState, "localPurgeState는 null일 수 없습니다");
    if (version <= 0) {
      throw new IllegalArgumentException("version은 양수여야 합니다");
    }
  }

  public static IncidentDataPurgeRun pending(
      UUID purgeRunId,
      UUID incidentId,
      Instant closedAt,
      PurgeEnvironmentPolicy environmentPolicy) {
    return new IncidentDataPurgeRun(
        purgeRunId,
        incidentId,
        IncidentDataPurgeStatus.PENDING,
        closedAt,
        environmentPolicy.purgeDueAt(closedAt),
        null,
        null,
        1L,
        environmentPolicy,
        LocalPurgeState.PURGE_PENDING);
  }

  public IncidentDataPurgeRun transition(
      IncidentDataPurgeStatus nextStatus,
      Instant completedAt,
      String lastErrorCode,
      LocalPurgeState nextLocalPurgeState) {
    return new IncidentDataPurgeRun(
        purgeRunId,
        incidentId,
        nextStatus,
        closedAt,
        purgeDeadlineTs,
        completedAt,
        lastErrorCode,
        version + 1,
        environmentPolicy,
        nextLocalPurgeState);
  }
}
