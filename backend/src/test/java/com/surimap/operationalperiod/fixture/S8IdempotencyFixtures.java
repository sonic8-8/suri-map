package com.surimap.operationalperiod.fixture;

import java.util.List;
import java.util.UUID;

/** S8 write idempotency replay/mismatch fixture. */
public final class S8IdempotencyFixtures {

  /** 같은 멱등 키와 같은 body hash로 재전송했을 때 기존 결과를 재사용해야 하는 기준값. */
  public static final String IDEMPOTENCY_KEY = "idem-s8-op-transition-001";

  public static final String IDEMPOTENT_BODY_HASH_ORIGINAL = "sha256:s8-op-transition-body-001";
  public static final String IDEMPOTENT_BODY_HASH_CHANGED = "sha256:s8-op-transition-body-002";
  public static final String IDEMPOTENT_ENDPOINT = "POST /operational-periods";
  public static final String IDEMPOTENCY_MISMATCH_ERROR = "idempotency_mismatch";
  public static final int IDEMPOTENT_SAME_BODY_PUBLISH_REQUEST_COUNT = 1;
  public static final int IDEMPOTENT_MISMATCH_NEW_ROW_COUNT = 0;
  public static final int IDEMPOTENT_MISMATCH_NEW_PUBLISH_REQUEST_COUNT = 0;

  /** S8 write API가 공통 멱등 replay 정책을 적용해야 하는 endpoint 목록. */
  public static final List<String> IDEMPOTENT_COVERED_ENDPOINTS =
      List.of(
          "POST /operational-periods",
          "POST /operational-periods/{opId}/assignments",
          "POST /handover-memos",
          "PATCH /duty-shifts/{dutyShiftId}");

  private S8IdempotencyFixtures() {}

  /** 같은 body hash 재전송이 새 row/event 없이 기존 응답으로 수렴하는지 확인하는 데이터. */
  public static IdempotentSameBodyReplay idempotentSameBodyReplay() {
    return new IdempotentSameBodyReplay(
        IDEMPOTENCY_KEY,
        IDEMPOTENT_BODY_HASH_ORIGINAL,
        IDEMPOTENT_ENDPOINT,
        OperationalPeriodFixtures.NEW_OP_ID,
        OperationalPeriodFixtures.NEW_OP_STATUS,
        OperationalPeriodFixtures.NEW_OP_VERSION,
        IDEMPOTENT_SAME_BODY_PUBLISH_REQUEST_COUNT);
  }

  /** 같은 멱등 키에 다른 body hash가 들어왔을 때 mismatch로 거부하는지 확인하는 데이터. */
  public static IdempotentMismatch idempotentMismatch() {
    return new IdempotentMismatch(
        IDEMPOTENCY_KEY,
        IDEMPOTENT_BODY_HASH_ORIGINAL,
        IDEMPOTENT_BODY_HASH_CHANGED,
        IDEMPOTENCY_MISMATCH_ERROR,
        IDEMPOTENT_MISMATCH_NEW_ROW_COUNT,
        IDEMPOTENT_MISMATCH_NEW_PUBLISH_REQUEST_COUNT);
  }

  /** 같은 body hash 멱등 재전송 비교 모델. */
  public record IdempotentSameBodyReplay(
      String idempotencyKey,
      String bodyHash,
      String endpoint,
      UUID expectedResponseId,
      String expectedStatus,
      long expectedVersion,
      int expectedPublishRequestCount) {}

  /** 다른 body hash 멱등 충돌 비교 모델. */
  public record IdempotentMismatch(
      String idempotencyKey,
      String originalBodyHash,
      String changedBodyHash,
      String expectedError,
      int expectedNewRowCount,
      int expectedNewPublishRequestCount) {}
}
