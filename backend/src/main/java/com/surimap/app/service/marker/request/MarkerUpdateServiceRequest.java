package com.surimap.app.service.marker.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.surimap.global.auth.SuriMapAuthentication;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonPropertyOrder({"version", "location", "memo", "type"})
public class MarkerUpdateServiceRequest {

  // URL의 마커 ID와 인증 정보·요청 키는 JSON 본문 비교에서 제외한다.
  @JsonIgnore private UUID markerId;
  @JsonIgnore private SuriMapAuthentication authentication;
  @JsonIgnore private String idempotencyKey;
  private Long version;
  private MarkerGeoJsonPoint location;
  private String memo;
  private String type;

  @Builder(toBuilder = true)
  private MarkerUpdateServiceRequest(
      UUID markerId,
      SuriMapAuthentication authentication,
      String idempotencyKey,
      Long version,
      MarkerGeoJsonPoint location,
      String memo,
      String type) {
    this.markerId = markerId;
    this.authentication = authentication;
    this.idempotencyKey = idempotencyKey;
    this.version = version;
    this.location = location;
    this.memo = memo;
    this.type = type;
  }
}
