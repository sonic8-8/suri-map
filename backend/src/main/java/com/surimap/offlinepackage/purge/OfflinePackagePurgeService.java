package com.surimap.offlinepackage.purge;

import com.surimap.offlinepackage.service.OfflinePackageRepository;
import com.surimap.retention.purge.PurgeHookResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfflinePackagePurgeService implements PackagePurgeHook {

  private final OfflinePackageRepository repository;

  public OfflinePackagePurgeService(OfflinePackageRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public PurgeHookResult purgeIncidentPackage(
      UUID incidentId, UUID purgeRunId, Instant closedAt, Instant purgeDeadlineTs) {
    return PurgeHookResult.succeeded(repository.purgeIncidentPackage(incidentId), 0);
  }
}
