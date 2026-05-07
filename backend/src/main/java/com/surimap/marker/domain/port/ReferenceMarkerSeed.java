package com.surimap.marker.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * S1-1 import transaction에서 S5 초기 기준 마커 생성을 호출하는 contract port.
 *
 * <p>실제 marker row 생성 구현체는 S5 production adapter가 담당한다.
 */
public interface ReferenceMarkerSeed {

  /**
   * import 트랜잭션 안에서 mock 112 원천 marker를 S5 marker row로 생성한다.
   *
   * <p>실패하면 호출자인 L1 import가 전체 rollback한다.
   */
  void createForIncident(UUID incidentId, List<SeedMarker> seedMarkers);

  /** mock 112 seedMarkers[] 원천값. Suri-Map 내부 marker ID는 S5 구현체가 결정한다. */
  record SeedMarker(String type, String source, String memo, double lon, double lat) {}
}
