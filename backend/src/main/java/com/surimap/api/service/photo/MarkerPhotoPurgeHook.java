package com.surimap.api.service.photo;

import com.surimap.retention.purge.PurgeHookResult;
import java.time.Instant;
import java.util.UUID;

/** 사건의 마커와 사진을 파기하도록 요청하는 내부 계약. */
@FunctionalInterface
public interface MarkerPhotoPurgeHook {

  PurgeHookResult purgeIncidentMarkerPhotos(
      UUID incidentId, UUID purgeRunId, Instant closedAt, Instant purgeDeadlineTs);
}
