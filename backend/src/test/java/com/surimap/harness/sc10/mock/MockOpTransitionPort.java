package com.surimap.harness.sc10.mock;

import com.surimap.harness.sc10.fixture.Sc10Fixtures;
import com.surimap.operationalperiod.command.ManualOpTransitionCommand;
import com.surimap.operationalperiod.command.ManualOpTransitionRequest;
import com.surimap.operationalperiod.command.ManualOpTransitionResult;
import com.surimap.operationalperiod.command.OpTransitionedEvent;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.util.ArrayList;
import java.util.List;

/**
 * SC-10 harness OP 전환 mock port.
 *
 * <p>기준 문서: docs/spec/specs/S8.json §harness_fixtures.sc10_op_transition_convergence.
 */
public class MockOpTransitionPort implements ManualOpTransitionCommand {

  private final List<ManualOpTransitionRequest> capturedRequests = new ArrayList<>();
  private ManualOpTransitionResult stubbedResult;

  public MockOpTransitionPort stubSuccess() {
    OperationalPeriodRow previousOp = OperationalPeriodFixtures.endedOp1();
    OperationalPeriodRow newOp = OperationalPeriodFixtures.activeOp2(Sc10Fixtures.NEW_OP_REASON);
    OpTransitionedEvent event = new OpTransitionedEvent(
        OpTransitionedEvent.TYPE,
        Sc10Fixtures.INCIDENT_ID,
        Sc10Fixtures.OP2_ID,
        Sc10Fixtures.NEW_OP_STATUS,
        Sc10Fixtures.NEW_OP_VERSION,
        Sc10Fixtures.NEW_OP_SEQUENCE_NO,
        Sc10Fixtures.OP1_ID,
        Sc10Fixtures.OP2_ID);
    this.stubbedResult = new ManualOpTransitionResult(newOp, previousOp, event);
    return this;
  }

  @Override
  public ManualOpTransitionResult transition(ManualOpTransitionRequest request) {
    capturedRequests.add(request);
    if (stubbedResult != null) {
      return stubbedResult;
    }
    throw new IllegalStateException("MockOpTransitionPort not stubbed");
  }

  public List<ManualOpTransitionRequest> capturedRequests() {
    return List.copyOf(capturedRequests);
  }

  public ManualOpTransitionResult stubbedResult() {
    return stubbedResult;
  }

  /** board convergence: OP 전환 후 현재 ACTIVE OP ID를 반환한다. */
  public java.util.Optional<java.util.UUID> currentActiveOpId() {
    if (stubbedResult != null) {
      return java.util.Optional.of(stubbedResult.newOp().opId());
    }
    return java.util.Optional.empty();
  }
}
