package com.surimap.operationalperiod.testdouble;

import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.util.UUID;

/** L3-T05A @RequireCurrentOp mock guard. */
public final class CurrentOpGuardMock {

  private final OperationalPeriodQuery query;

  public CurrentOpGuardMock(OperationalPeriodQuery query) {
    this.query = query;
  }

  public OperationalPeriodRow requireCurrent(UUID incidentId, UUID payloadOpId) {
    OperationalPeriodRow current =
        query.current(incidentId).orElseThrow(() -> new CurrentOpGuardException("op_required"));
    if (!current.opId().equals(payloadOpId)) {
      throw new CurrentOpGuardException("op_mismatch");
    }
    return current;
  }
}
