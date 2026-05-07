package com.surimap.incident.adapter;

import com.surimap.marker.domain.port.ReferenceMarkerSeed;
import java.util.List;
import java.util.UUID;

/** S5 구현체가 없을 때 초기 기준 마커 없는 partial import를 만들지 않도록 실패시키는 fallback. */
public class BlockingReferenceMarkerSeed implements ReferenceMarkerSeed {

  @Override
  public void createForIncident(UUID incidentId, List<SeedMarker> seedMarkers) {
    throw new IllegalStateException("reference_marker_seed_unavailable");
  }
}
