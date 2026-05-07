package com.surimap.marker.seed;

import com.surimap.marker.query.MarkerView;
import java.util.List;
import java.util.UUID;

public record ReferenceMarkerSeedResult(UUID incidentId, List<MarkerView> markers) {

  public ReferenceMarkerSeedResult {
    markers = List.copyOf(markers);
  }
}
