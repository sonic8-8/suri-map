package com.surimap.api.service.marker.response;

import com.surimap.marker.query.MarkerQueryResult;
import com.surimap.marker.query.MarkerView;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MarkerListServiceResponse {

  private UUID incidentId;
  private List<MarkerView> markers;

  @Builder
  private MarkerListServiceResponse(UUID incidentId, List<MarkerView> markers) {
    this.incidentId = incidentId;
    this.markers = markers;
  }

  public static MarkerListServiceResponse from(MarkerQueryResult result) {
    return MarkerListServiceResponse.builder()
        .incidentId(result.incidentId())
        .markers(result.markers())
        .build();
  }
}
