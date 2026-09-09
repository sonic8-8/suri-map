package com.surimap.api.controller.marker.request;

import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.marker.service.request.MarkerUpdateServiceRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MarkerUpdateRequest {

  @NotNull @Positive private Long version;
  private MarkerGeoJsonPoint location;
  private String memo;
  private String type;

  @Builder
  private MarkerUpdateRequest(Long version, MarkerGeoJsonPoint location, String memo, String type) {
    this.version = version;
    this.location = location;
    this.memo = memo;
    this.type = type;
  }

  public MarkerUpdateServiceRequest toServiceRequest(UUID markerId, MarkerRequestContext context) {
    return MarkerUpdateServiceRequest.builder()
        .markerId(markerId)
        .version(version)
        .location(location)
        .memo(memo)
        .type(type)
        .context(context)
        .build();
  }
}
