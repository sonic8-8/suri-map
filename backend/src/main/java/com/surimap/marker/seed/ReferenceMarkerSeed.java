package com.surimap.marker.seed;

import java.util.List;
import java.util.UUID;

/**
 * S1-1 incident import가 초기 기준 마커 생성을 위임하는 S5 port.
 *
 * <p>S1/S7은 marker write를 소유하지 않고 이 port와 반환 read shape만 소비한다.
 */
public interface ReferenceMarkerSeed {

  ReferenceMarkerSeedResult createForIncident(UUID incidentId, List<SeedMarker> seedMarkers);
}
