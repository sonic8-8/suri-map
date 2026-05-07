package com.surimap.marker.adapter;

import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.port.MarkerWriteGuardPort;
import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * S1-1/S1-2/S8 guard 구현 전까지 public marker create writes를 fail-closed로 막는 adapter.
 *
 * <p>S5 owns this port contract; incident/PolicePhone/current OP ownership stays in provider lanes.
 */
@Component
public class BlockingMarkerWriteGuardAdapter implements MarkerWriteGuardPort {

  @Override
  public void requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context) {
    throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }

  @Override
  public MarkerMutationContext requireUpdateAccess(UUID markerId, MarkerRequestContext context) {
    throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }

  @Override
  public MarkerMutationContext requireDeleteAccess(UUID markerId, MarkerRequestContext context) {
    throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }
}
