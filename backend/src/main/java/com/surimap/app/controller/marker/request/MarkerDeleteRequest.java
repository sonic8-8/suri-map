package com.surimap.app.controller.marker.request;

import com.surimap.app.service.marker.request.MarkerDeleteServiceRequest;
import com.surimap.marker.service.MarkerRequestContext;
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

  public MarkerDeleteServiceRequest toServiceRequest(UUID markerId, MarkerRequestContext context) {
    return MarkerDeleteServiceRequest.builder()
        .markerId(markerId)
        .version(version)
        .reason(reason)
        .context(context)
        .build();
  }
}
