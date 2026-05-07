package com.surimap.operationalperiod.query;

import com.surimap.operationalperiod.OperationalPeriod;
import com.surimap.operationalperiod.OperationalPeriodMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** OperationalPeriodQuery 구현체. DB에서 current OP와 OP 목록을 조회한다 (S8.json §service_contracts). */
@Service
public class OperationalPeriodQueryService implements OperationalPeriodQuery {

  private final OperationalPeriodMapper mapper;

  public OperationalPeriodQueryService(OperationalPeriodMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<CurrentOpResult> current(UUID incidentId) {
    return mapper.findActiveByIncident(incidentId).map(this::toCurrentOpResult);
  }

  @Override
  public List<OperationalPeriodRow> list(UUID incidentId) {
    return mapper.findAllByIncidentOrderBySequence(incidentId).stream().map(this::toRow).toList();
  }

  private CurrentOpResult toCurrentOpResult(OperationalPeriod op) {
    return new CurrentOpResult(
        op.getId(),
        op.getIncidentId(),
        op.getStatus(),
        op.getSequenceNumber(),
        op.getStartedAt(),
        op.getEndedAt(),
        op.getReason(),
        op.getVersion());
  }

  private OperationalPeriodRow toRow(OperationalPeriod op) {
    return new OperationalPeriodRow(
        op.getId(),
        op.getIncidentId(),
        op.getStatus(),
        op.getSequenceNumber(),
        op.getStartedAt(),
        op.getEndedAt(),
        op.getReason(),
        op.getVersion());
  }
}
