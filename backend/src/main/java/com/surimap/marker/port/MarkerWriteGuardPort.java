package com.surimap.marker.port;

import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
import java.util.UUID;

public interface MarkerWriteGuardPort {

  void requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context);

  MarkerMutationContext requireUpdateAccess(UUID markerId, MarkerRequestContext context);

  MarkerMutationContext requireDeleteAccess(UUID markerId, MarkerRequestContext context);
}
