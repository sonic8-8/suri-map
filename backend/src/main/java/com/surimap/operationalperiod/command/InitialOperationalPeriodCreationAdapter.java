package com.surimap.operationalperiod.command;

import com.surimap.operationalperiod.InitialOperationalPeriodCreationService;
import com.surimap.operationalperiod.OperationalPeriod;
import com.surimap.operationalperiod.OperationalPeriodMapper;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** S1-1 import port를 S8 OP1 생성 서비스에 연결하는 production adapter. */
@Component
public class InitialOperationalPeriodCreationAdapter implements InitialOperationalPeriodCreator {

  private static final int OP1_SEQUENCE = 1;

  private final InitialOperationalPeriodCreationService creationService;
  private final OperationalPeriodMapper mapper;

  public InitialOperationalPeriodCreationAdapter(
      InitialOperationalPeriodCreationService creationService, OperationalPeriodMapper mapper) {
    this.creationService = creationService;
    this.mapper = mapper;
  }

  @Override
  public InitialOperationalPeriodResult createOp1(UUID incidentId) {
    creationService.createOp1(incidentId);
    OperationalPeriod op =
        mapper
            .findByIncidentAndSequence(incidentId, OP1_SEQUENCE)
            .orElseThrow(() -> new IllegalStateException("op1_not_found_after_creation"));
    return new InitialOperationalPeriodResult(
        new OperationalPeriodRow(
            op.getId(),
            op.getIncidentId(),
            op.getStatus(),
            op.getSequenceNumber(),
            op.getStartedAt(),
            op.getEndedAt(),
            op.getReason(),
            op.getVersion()));
  }
}
