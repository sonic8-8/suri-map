package com.surimap.incident.service;

import com.surimap.incident.domain.MissingPersonConsumerView;
import com.surimap.incident.repository.IncidentMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Active missing_person row를 S1-1 소비자용 projection으로 읽는다. */
@Service
public class MissingPersonQueryService {

  private final IncidentMapper incidentMapper;

  public MissingPersonQueryService(IncidentMapper incidentMapper) {
    this.incidentMapper = incidentMapper;
  }

  @Transactional(readOnly = true)
  public Optional<MissingPersonConsumerView> findActiveConsumerView(UUID incidentId) {
    return incidentMapper
        .findMissingPersonByIncidentId(incidentId)
        .map(MissingPersonConsumerView::from);
  }
}
