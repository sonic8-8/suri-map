package com.surimap.operationalperiod.fixture;

import com.surimap.operationalperiod.query.OperationalPeriodRow;

/** OperationalPeriodQuery mock 응답 fixture (S8.json §service_contracts). */
public final class OperationalPeriodQueryFixtures {

  private OperationalPeriodQueryFixtures() {}

  /** current(incidentId) / list(incidentId) mock 결과 — OP1 ACTIVE row. */
  public static OperationalPeriodRow currentOp1() {
    return OperationalPeriodFixtures.currentOp1();
  }
}
