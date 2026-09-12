package com.surimap.api.service.marker;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사건 가져오기 트랜잭션에서 초기 기준 마커 생성을 요청하는 내부 계약.
 *
 * <p>실제 저장은 {@link ReferenceMarkerSeedService}가 담당한다.
 */
public interface ReferenceMarkerSeed {

  /**
   * 사건 가져오기 트랜잭션 안에서 mock 112의 원천 마커를 기준 마커로 저장한다.
   *
   * <p>실패하면 호출자가 사건 가져오기 전체를 롤백한다.
   */
  void createForIncident(UUID incidentId, List<SeedMarker> seedMarkers);

  /** mock 112 seedMarkers[]의 원천값. 내부 마커 ID는 저장하는 서비스가 결정한다. */
  @Getter
  @NoArgsConstructor
  class SeedMarker {

    private String type;
    private String source;
    private String memo;
    private double lon;
    private double lat;

    @Builder
    public SeedMarker(String type, String source, String memo, double lon, double lat) {
      this.type = type;
      this.source = source;
      this.memo = memo;
      this.lon = lon;
      this.lat = lat;
    }
  }
}
