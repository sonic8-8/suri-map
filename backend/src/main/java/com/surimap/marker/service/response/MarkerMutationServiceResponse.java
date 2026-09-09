package com.surimap.marker.service.response;

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
}
