package com.surimap.retention.purge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal/system-only purge status source for server-side API assembly. */
@Service
public class RetentionPurgeStatus {

  private final PurgeRunRepository purgeRunRepository;

  public RetentionPurgeStatus(PurgeRunRepository purgeRunRepository) {
    this.purgeRunRepository = purgeRunRepository;
  }

  @Transactional(readOnly = true)
  public Optional<RetentionPurgeStatusSnapshot> byIncident(UUID incidentId) {
    return purgeRunRepository.findByIncidentId(incidentId).map(RetentionPurgeStatusSnapshot::from);
  }
}
