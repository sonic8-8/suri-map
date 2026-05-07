package com.surimap.incident.lifecycle;

import com.surimap.incident.domain.IncidentRecord;
import com.surimap.incident.repository.IncidentMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis incident row를 lifecycle guard용 snapshot으로 변환한다. */
@Service
public class IncidentLifecycleQueryService implements IncidentLifecycleQuery {

  private final IncidentMapper incidentMapper;

  public IncidentLifecycleQueryService(IncidentMapper incidentMapper) {
    this.incidentMapper = incidentMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<IncidentLifecycleSnapshot> findByIncidentId(UUID incidentId) {
    return incidentMapper.findByIncidentId(incidentId).map(this::toSnapshot);
  }

  private IncidentLifecycleSnapshot toSnapshot(IncidentRecord record) {
    return new IncidentLifecycleSnapshot(record.getId(), record.getStatus(), record.getVersion());
  }
}
