package com.surimap.operationalperiod.command;

import java.util.Objects;
import java.util.UUID;

/**
 * OP2+ 수동 전환 요청 (S8.json §api_contracts POST /operational-periods request_schema).
 *
 * <p>reason은 RE_SEARCH | AREA_CHANGED | OTHER. reason=OTHER 시 reasonMemo 필수.
 */
public record ManualOpTransitionRequest(
    UUID incidentId,
    String reason,
    String reasonMemo,
    String handoverMemo,
    String idempotencyKey) {

  public ManualOpTransitionRequest {
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    if ("OTHER".equals(reason) && reasonMemo == null) {
      throw new IllegalArgumentException("reason=OTHER requires reasonMemo");
    }
  }
}
