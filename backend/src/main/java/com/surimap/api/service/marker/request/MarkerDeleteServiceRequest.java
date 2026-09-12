package com.surimap.api.service.marker.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.surimap.global.auth.SuriMapAuthentication;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonPropertyOrder({"version", "reason"})
public class MarkerDeleteServiceRequest {

  // URL의 마커 ID와 인증 정보·요청 키는 JSON 본문 비교에서 제외한다.
  @JsonIgnore private UUID markerId;
  @JsonIgnore private SuriMapAuthentication authentication;
  @JsonIgnore private String idempotencyKey;
  private Long version;
  private String reason;

  @Builder(toBuilder = true)
  private MarkerDeleteServiceRequest(
      UUID markerId,
      SuriMapAuthentication authentication,
      String idempotencyKey,
      Long version,
      String reason) {
    this.markerId = markerId;
    this.authentication = authentication;
    this.idempotencyKey = idempotencyKey;
    this.version = version;
    this.reason = reason;
  }
}
