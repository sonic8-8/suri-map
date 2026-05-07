package com.surimap.operationalperiod.command;

import java.util.UUID;

/**
 * S1-1 import transaction에서 S8 OP1 자동 생성을 호출하는 contract port.
 *
 * <p>실제 OP1 생성 구현체는 S8 production adapter가 담당한다.
 */
public interface InitialOperationalPeriodCreator {

  /** 사건 import 직후 OP1을 만든다. 실패하면 호출자인 L1 import가 전체 rollback한다. */
  InitialOperationalPeriodResult createOp1(UUID incidentId);
}
