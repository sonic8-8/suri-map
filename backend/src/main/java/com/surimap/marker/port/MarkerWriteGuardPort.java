package com.surimap.marker.port;

import com.surimap.domain.marker.Marker;
import com.surimap.marker.service.MarkerRequestContext;
import java.util.UUID;

public interface MarkerWriteGuardPort {

  UUID requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context);

  Marker requireUpdateAccess(UUID markerId, MarkerRequestContext context);

  Marker requireDeleteAccess(UUID markerId, MarkerRequestContext context);
}
