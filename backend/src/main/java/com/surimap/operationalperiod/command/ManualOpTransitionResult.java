package com.surimap.operationalperiod.command;

import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.util.Objects;

/**
 * OP2+ 수동 전환 결과 (S8.json §harness_fixtures.sc10_op_transition_convergence).
 *
 * <p>newOp은 ACTIVE, previousOp은 ENDED, publishedEvent는 OP_TRANSITIONED payload.
 */
public record ManualOpTransitionResult(
    OperationalPeriodRow newOp, OperationalPeriodRow previousOp, OpTransitionedEvent publishedEvent) {

  public ManualOpTransitionResult {
    Objects.requireNonNull(newOp, "newOp must not be null");
    Objects.requireNonNull(previousOp, "previousOp must not be null");
    Objects.requireNonNull(publishedEvent, "publishedEvent must not be null");
  }
}
