package com.surimap.retention.purge;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public enum PurgeEnvironmentPolicy {
  PRODUCTION_IMMEDIATE(Duration.ZERO),
  DEMO_24H_SOFT_DELETE(Duration.ofHours(24));

  private final Duration delay;

  PurgeEnvironmentPolicy(Duration delay) {
    this.delay = delay;
  }

  public Instant purgeDueAt(Instant closedAt) {
    Objects.requireNonNull(closedAt, "closedAt는 null일 수 없습니다");
    return closedAt.plus(delay);
  }
}
