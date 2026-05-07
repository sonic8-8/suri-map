package com.surimap.retention.purge;

import java.util.Objects;

public record PurgeHookResult(
    PurgeHookStatus status, long purgedCount, long retainedCount, String errorCode) {

  public PurgeHookResult {
    Objects.requireNonNull(status, "status는 null일 수 없습니다");
    if (purgedCount < 0) {
      throw new IllegalArgumentException("purgedCount는 음수일 수 없습니다");
    }
    if (retainedCount < 0) {
      throw new IllegalArgumentException("retainedCount는 음수일 수 없습니다");
    }
  }

  public static PurgeHookResult succeeded(long purgedCount, long retainedCount) {
    return new PurgeHookResult(PurgeHookStatus.SUCCEEDED, purgedCount, retainedCount, null);
  }

  public static PurgeHookResult waitingForSync(
      long purgedCount, long retainedCount, String errorCode) {
    return new PurgeHookResult(
        PurgeHookStatus.WAITING_FOR_SYNC, purgedCount, retainedCount, errorCode);
  }

  public static PurgeHookResult failedRetryable(
      long purgedCount, long retainedCount, String errorCode) {
    return new PurgeHookResult(
        PurgeHookStatus.FAILED_RETRYABLE, purgedCount, retainedCount, errorCode);
  }
}
