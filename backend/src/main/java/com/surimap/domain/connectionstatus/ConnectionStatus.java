package com.surimap.domain.connectionstatus;

import java.time.Duration;
import java.time.Instant;

/** 계정의 통신 상태. 마지막 heartbeat를 받은 서버 시각과 현재 시각의 차이로 정한다. */
public enum ConnectionStatus {
  ONLINE,
  STALE,
  LOST;

  private static final Duration STALE_THRESHOLD = Duration.ofSeconds(60);
  private static final Duration LOST_THRESHOLD = Duration.ofMinutes(5);

  public static ConnectionStatus of(Instant lastHeartbeatAt, Instant now) {
    if (lastHeartbeatAt == null) {
      return LOST;
    }
    Duration age = Duration.between(lastHeartbeatAt, now);
    if (age.compareTo(STALE_THRESHOLD) < 0) {
      return ONLINE;
    }
    if (age.compareTo(LOST_THRESHOLD) < 0) {
      return STALE;
    }
    return LOST;
  }
}
