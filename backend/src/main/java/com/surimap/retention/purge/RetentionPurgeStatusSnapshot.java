package com.surimap.retention.purge;

import java.util.UUID;

public record RetentionPurgeStatusSnapshot(
    UUID incidentId,
    UUID purgeRunId,
    IncidentDataPurgeStatus status,
    long version,
    LocalPurgeState localPurgeState) {

  static RetentionPurgeStatusSnapshot from(IncidentDataPurgeRun run) {
    return new RetentionPurgeStatusSnapshot(
        run.incidentId(), run.purgeRunId(), run.status(), run.version(), run.localPurgeState());
  }
}
