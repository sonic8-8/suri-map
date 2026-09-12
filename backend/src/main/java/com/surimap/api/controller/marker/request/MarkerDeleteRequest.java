package com.surimap.api.controller.marker.request;

import com.surimap.api.service.marker.request.MarkerDeleteServiceRequest;
import com.surimap.global.auth.SuriMapAuthentication;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MarkerDeleteRequest {

  @NotNull @Positive private Long version;
  private String reason;

  @Builder
  private MarkerDeleteRequest(Long version, String reason) {
    this.version = version;
    this.reason = reason;
  }

  public MarkerDeleteServiceRequest toServiceRequest(
      UUID markerId, SuriMapAuthentication authentication, String idempotencyKey) {
    return MarkerDeleteServiceRequest.builder()
        .markerId(markerId)
        .version(version)
        .reason(reason)
        .authentication(authentication)
        .idempotencyKey(idempotencyKey)
        .build();
  }
}
