package com.surimap.marker.seed;

import com.surimap.api.service.marker.response.MarkerListServiceResponse.MarkerServiceResponse;
import java.util.List;
import java.util.UUID;

public record ReferenceMarkerSeedResult(UUID incidentId, List<MarkerServiceResponse> markers) {

  public ReferenceMarkerSeedResult {
    markers = List.copyOf(markers);
  }
}
