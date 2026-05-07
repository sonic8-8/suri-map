package com.surimap.retention.purge;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PurgeHookRequest(
    UUID incidentId, UUID purgeRunId, Instant closedAt, Instant purgeDeadlineTs) {

  public PurgeHookRequest {
    Objects.requireNonNull(incidentId, "incidentId는 null일 수 없습니다");
    Objects.requireNonNull(purgeRunId, "purgeRunId는 null일 수 없습니다");
    Objects.requireNonNull(closedAt, "closedAt은 null일 수 없습니다");
    Objects.requireNonNull(purgeDeadlineTs, "purgeDeadlineTs는 null일 수 없습니다");
  }
}
