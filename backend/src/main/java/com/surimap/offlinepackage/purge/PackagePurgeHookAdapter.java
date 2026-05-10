package com.surimap.offlinepackage.purge;

import com.surimap.retention.purge.PurgeHook;
import com.surimap.retention.purge.PurgeHookName;
import com.surimap.retention.purge.PurgeHookRequest;
import com.surimap.retention.purge.PurgeHookResult;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Adapts the S7 package purge contract to the S1-3 purge hook boundary. */
@Component
public final class PackagePurgeHookAdapter implements PurgeHook {

  private final PackagePurgeHook packagePurgeHook;

  public PackagePurgeHookAdapter(PackagePurgeHook packagePurgeHook) {
    this.packagePurgeHook =
        Objects.requireNonNull(packagePurgeHook, "packagePurgeHook는 null일 수 없습니다");
  }

  @Override
  public PurgeHookName name() {
    return PurgeHookName.OFFLINE_PACKAGE;
  }

  @Override
  public PurgeHookResult purge(PurgeHookRequest request) {
    Objects.requireNonNull(request, "request는 null일 수 없습니다");
    return packagePurgeHook.purgeIncidentPackage(
        request.incidentId(),
        request.purgeRunId(),
        request.closedAt(),
        request.purgeDeadlineTs());
  }
}
