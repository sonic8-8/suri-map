package com.surimap.api.service.photo;

import com.surimap.retention.purge.PurgeHook;
import com.surimap.retention.purge.PurgeHookName;
import com.surimap.retention.purge.PurgeHookRequest;
import com.surimap.retention.purge.PurgeHookResult;
import java.util.Objects;

/** 공통 파기 요청을 마커·사진 파기 계약에 맞춰 전달한다. */
public final class MarkerPhotoPurgeHookAdapter implements PurgeHook {

  private final MarkerPhotoPurgeHook markerPhotoPurgeHook;

  public MarkerPhotoPurgeHookAdapter(MarkerPhotoPurgeHook markerPhotoPurgeHook) {
    this.markerPhotoPurgeHook =
        Objects.requireNonNull(markerPhotoPurgeHook, "markerPhotoPurgeHook는 null일 수 없습니다");
  }

  @Override
  public PurgeHookName name() {
    return PurgeHookName.MARKER_PHOTO;
  }

  @Override
  public PurgeHookResult purge(PurgeHookRequest request) {
    Objects.requireNonNull(request, "request는 null일 수 없습니다");
    return markerPhotoPurgeHook.purgeIncidentMarkerPhotos(
        request.incidentId(), request.purgeRunId(), request.closedAt(), request.purgeDeadlineTs());
  }
}
