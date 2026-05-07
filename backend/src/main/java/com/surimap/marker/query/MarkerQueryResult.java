package com.surimap.marker.query;

import java.util.List;
import java.util.UUID;

/** MarkerQuery.byIncident collection result. */
public record MarkerQueryResult(UUID incidentId, List<MarkerView> markers) {

  public MarkerQueryResult {
    markers = List.copyOf(markers);
  }
}
