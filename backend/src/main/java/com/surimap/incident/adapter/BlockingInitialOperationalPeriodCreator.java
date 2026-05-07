package com.surimap.incident.adapter;

import com.surimap.operationalperiod.command.InitialOperationalPeriodCreator;
import com.surimap.operationalperiod.command.InitialOperationalPeriodResult;
import java.util.UUID;

/** S8 구현체가 없을 때 OP1 없는 partial import를 만들지 않도록 실패시키는 fallback. */
public class BlockingInitialOperationalPeriodCreator implements InitialOperationalPeriodCreator {

  @Override
  public InitialOperationalPeriodResult createOp1(UUID incidentId) {
    throw new IllegalStateException("initial_operational_period_creator_unavailable");
  }
}
