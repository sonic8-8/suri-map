package com.surimap.api.controller.marker.response;

import com.surimap.marker.service.response.MarkerMutationServiceResponse;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MarkerMutationResponse {

  private UUID id;
  private String status;
  private long version;

  @Builder
  private MarkerMutationResponse(UUID id, String status, long version) {
    this.id = id;
    this.status = status;
    this.version = version;
  }

  public static MarkerMutationResponse from(MarkerMutationServiceResponse response) {
    return MarkerMutationResponse.builder()
        .id(response.getId())
        .status(response.getStatus())
        .version(response.getVersion())
        .build();
  }
}
