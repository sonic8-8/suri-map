package com.surimap.operationalperiod.testdouble;

import com.surimap.operationalperiod.command.ManualOpTransitionCommand;
import com.surimap.operationalperiod.command.ManualOpTransitionRequest;
import com.surimap.operationalperiod.command.ManualOpTransitionResult;
import com.surimap.operationalperiod.command.OpTransitionedEvent;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * L3-T06A OP2+ 수동 전환 mock test double.
 *
 * <p>real DB 없이 web commander가 reason과 함께 OP2+를 열고 이전 active OP를 닫으며 OP_TRANSITIONED를 발행하는
 * contract를 검증한다.
 *
 * <p>S8.json §harness_fixtures.sc10_op_transition_convergence 기준.
 */
public final class ManualOpTransitionMock implements ManualOpTransitionCommand {

  private final Map<String, ManualOpTransitionResult> replayCache = new LinkedHashMap<>();
  private final Map<String, RuntimeException> transitionFailures = new LinkedHashMap<>();
  private final List<ManualOpTransitionResult> transitions = new ArrayList<>();

  @Override
  public ManualOpTransitionResult transition(ManualOpTransitionRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    if (!OperationalPeriodFixtures.INCIDENT_ID.equals(request.incidentId())) {
      throw new IllegalArgumentException("unknown incidentId: " + request.incidentId());
    }
    // ManualOpTransitionRequest constructor already enforces this, but guard here for clarity
    if ("OTHER".equals(request.reason()) && request.reasonMemo() == null) {
      throw new IllegalArgumentException("reason=OTHER requires reasonMemo");
    }

    RuntimeException failure = transitionFailures.get(request.idempotencyKey());
    if (failure != null) {
      throw failure;
    }

    // idempotency: same key replays cached response
    ManualOpTransitionResult cached = replayCache.get(request.idempotencyKey());
    if (cached != null) {
      return cached;
    }

    OperationalPeriodRow previousOp = OperationalPeriodFixtures.endedOp1();
    OperationalPeriodRow newOp = OperationalPeriodFixtures.activeOp2(request.reason());
    OpTransitionedEvent event =
        new OpTransitionedEvent(
            OpTransitionedEvent.TYPE,
            OperationalPeriodFixtures.INCIDENT_ID,
            OperationalPeriodFixtures.OP2_ID,
            "ACTIVE",
            1L,
            2,
            OperationalPeriodFixtures.OP1_ID,
            OperationalPeriodFixtures.OP2_ID);

    ManualOpTransitionResult result = new ManualOpTransitionResult(newOp, previousOp, event);
    replayCache.put(request.idempotencyKey(), result);
    transitions.add(result);
    return result;
  }

  public void failTransitionFor(String idempotencyKey, RuntimeException failure) {
    transitionFailures.put(
        Objects.requireNonNull(idempotencyKey), Objects.requireNonNull(failure));
  }

  public List<ManualOpTransitionResult> transitions() {
    return List.copyOf(transitions);
  }
}
