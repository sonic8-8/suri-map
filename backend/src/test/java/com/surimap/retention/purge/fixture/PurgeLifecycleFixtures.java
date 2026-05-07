package com.surimap.retention.purge.fixture;

import com.surimap.retention.purge.PurgeHookRequest;
import com.surimap.retention.purge.PurgeHookResult;
import com.surimap.retention.purge.testdouble.MockPurgeHookRegistry;
import java.time.Instant;
import java.util.UUID;

/** 모의 훅과 오케스트레이션 테스트에서 사용하는 안정적인 S1-3 파기 생명주기 고정 데이터. */
public final class PurgeLifecycleFixtures {

  public static final String INCIDENT_ALIAS = "inc-precinct-first-001";
  public static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");

  public static final String PURGE_RUN_ALIAS = "purge-run-inc-precinct-first-001";
  public static final UUID PURGE_RUN_ID = UUID.fromString("77777777-7777-7777-7777-777777770001");

  public static final Instant CLOSED_AT = Instant.parse("2026-04-28T01:30:00Z");
  public static final Instant PURGE_DEADLINE_TS = Instant.parse("2026-04-29T01:30:00Z");

  public static final String WAITING_FOR_SYNC_ERROR_CODE = "waiting_for_sync";
  public static final String RETRYABLE_FAILURE_ERROR_CODE = "purge_hook_failed";

  private PurgeLifecycleFixtures() {}

  public static PurgeHookRequest purgeHookRequest() {
    return new PurgeHookRequest(INCIDENT_ID, PURGE_RUN_ID, CLOSED_AT, PURGE_DEADLINE_TS);
  }

  public static MockPurgeHookRegistry defaultRegistry() {
    return MockPurgeHookRegistry.createDefault();
  }

  public static PurgeHookResult successResult() {
    return successResult(0L, 0L);
  }

  public static PurgeHookResult successResult(long purgedCount, long retainedCount) {
    return PurgeHookResult.succeeded(purgedCount, retainedCount);
  }

  public static PurgeHookResult waitingForSyncResult() {
    return waitingForSyncResult(0L, 1L);
  }

  public static PurgeHookResult waitingForSyncResult(long purgedCount, long retainedCount) {
    return PurgeHookResult.waitingForSync(purgedCount, retainedCount, WAITING_FOR_SYNC_ERROR_CODE);
  }

  public static PurgeHookResult retryableFailureResult() {
    return retryableFailureResult(0L, 1L, RETRYABLE_FAILURE_ERROR_CODE);
  }

  public static PurgeHookResult retryableFailureResult(
      long purgedCount, long retainedCount, String errorCode) {
    return PurgeHookResult.failedRetryable(purgedCount, retainedCount, errorCode);
  }
}
