package com.surimap.marker.query;

import java.util.UUID;

/** S5-owned internal marker read model consumed by board and OP history. */
public interface MarkerQuery {

  MarkerQueryResult byIncident(UUID incidentId, MarkerQueryFilters filters);
}
