package com.surimap.api.service.marker.response;

import com.surimap.domain.marker.Marker;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MarkerMutationServiceResponse {

  private UUID id;
  private String status;
  private long version;

  @Builder
  private MarkerMutationServiceResponse(UUID id, String status, long version) {
    this.id = id;
    this.status = status;
    this.version = version;
  }

  public static MarkerMutationServiceResponse from(Marker marker) {
    return MarkerMutationServiceResponse.builder()
        .id(marker.getId())
        .status(marker.getStatus())
        .version(marker.getVersion())
        .build();
  }
}
