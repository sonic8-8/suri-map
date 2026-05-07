package com.surimap.operationalperiod.command;

import java.util.UUID;

/**
 * S1-1 import transaction에서 S8 OP1 자동 생성을 호출하는 contract port.
 *
 * <p>실제 OP1 생성 구현체는 S8 production adapter가 담당한다.
 */
public interface InitialOperationalPeriodCreator {

  InitialOperationalPeriodResult createOp1(UUID incidentId);
}
