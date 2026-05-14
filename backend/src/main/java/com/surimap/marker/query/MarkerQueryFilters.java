package com.surimap.marker.query;

import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import java.util.UUID;

/** Optional MarkerQuery.byIncident filters. Null status means ACTIVE and UPDATED only. */
public record MarkerQueryFilters(UUID opId, MarkerType type, MarkerStatus status) {

  public static MarkerQueryFilters empty() {
    return new MarkerQueryFilters(null, null, null);
  }
}
