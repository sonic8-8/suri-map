package com.surimap.marker.adapter;

import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.port.MarkerWriteGuardPort;
import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runtime S5 marker write guard. Create is opened for APP marker writes; update/delete stay
 * fail-closed until the S5 marker policy for those mutations is connected.
 */
@Component
public class RuntimeMarkerWriteGuardAdapter implements MarkerWriteGuardPort {

  private final MarkerRuntimeGuardMapper markerRuntimeGuardMapper;

  public RuntimeMarkerWriteGuardAdapter(MarkerRuntimeGuardMapper markerRuntimeGuardMapper) {
    this.markerRuntimeGuardMapper = markerRuntimeGuardMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public void requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context) {
    requireAppContext(context);
    UUID accountId = context.authentication().accountId();
    UUID policePhoneId = context.authentication().policePhoneId();

    requireOpenIncident(incidentId);
    requireAccountAssignment(incidentId, accountId);
    requireRegisteredPolicePhone(policePhoneId, accountId);
    requirePolicePhoneAssignedToIncident(policePhoneId, incidentId);
  }

  @Override
  public MarkerMutationContext requireUpdateAccess(UUID markerId, MarkerRequestContext context) {
    throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }

  @Override
  public MarkerMutationContext requireDeleteAccess(UUID markerId, MarkerRequestContext context) {
    throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }

  private void requireAppContext(MarkerRequestContext context) {
    if (context == null || context.authentication() == null) {
      throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
    if (!"APP".equals(context.authentication().channel())) {
      throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
  }

  private void requireOpenIncident(UUID incidentId) {
    String status =
        markerRuntimeGuardMapper
            .findIncidentStatus(incidentId)
            .orElseThrow(
                () -> new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN));
    if ("OPEN".equals(status)) {
      return;
    }
    if ("CLOSED".equals(status)) {
      throw new MarkerApiException("incident_closed", HttpStatus.CONFLICT);
    }
    throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }

  private void requireAccountAssignment(UUID incidentId, UUID accountId) {
    if (markerRuntimeGuardMapper.countActiveAssignmentsByAccountId(accountId) == 0) {
      throw new MarkerApiException("team_not_assigned", HttpStatus.FORBIDDEN);
    }
    if (markerRuntimeGuardMapper.countActiveIncidentAssignment(incidentId, accountId) == 0) {
      throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
  }

  private void requireRegisteredPolicePhone(UUID policePhoneId, UUID accountId) {
    if (policePhoneId == null
        || markerRuntimeGuardMapper.countRegisteredPolicePhoneForAccount(policePhoneId, accountId)
            == 0) {
      throw new MarkerApiException("police_phone_not_registered", HttpStatus.FORBIDDEN);
    }
  }

  private void requirePolicePhoneAssignedToIncident(UUID policePhoneId, UUID incidentId) {
    if (markerRuntimeGuardMapper.countPolicePhoneAssignmentToIncident(policePhoneId, incidentId)
        == 0) {
      throw new MarkerApiException("police_phone_not_assigned", HttpStatus.FORBIDDEN);
    }
  }
}
