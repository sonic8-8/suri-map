package com.surimap.operationalperiod.command;

import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.util.Objects;

/** OP1 자동 생성 결과 contract. */
public record InitialOperationalPeriodResult(OperationalPeriodRow operationalPeriod) {

  public InitialOperationalPeriodResult {
    Objects.requireNonNull(operationalPeriod, "operationalPeriod must not be null");
  }
}
