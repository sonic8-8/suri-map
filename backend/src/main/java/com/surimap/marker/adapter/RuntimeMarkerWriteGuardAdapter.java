package com.surimap.marker.adapter;

import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerAccessMapper;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.service.MarkerRequestContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runtime S5 marker write guard. APP creates field markers, APP can update/delete its own field
 * markers, and WEB can correct seed/reference markers.
 */
@Component
public class RuntimeMarkerWriteGuardAdapter {

  private final MarkerAccessMapper markerAccessMapper;
  private final MarkerMapper markerMapper;

  public RuntimeMarkerWriteGuardAdapter(
      MarkerAccessMapper markerAccessMapper, MarkerMapper markerMapper) {
    this.markerAccessMapper = markerAccessMapper;
    this.markerMapper = markerMapper;
  }

  @Transactional(readOnly = true)
  public UUID requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context) {
    requireAppContext(context);
    UUID accountId = context.authentication().accountId();

    requireOpenIncident(incidentId);
    requireCurrentOp(incidentId, opId);
    requireAccountAssignment(incidentId, accountId);
    return markerAccessMapper
        .findActiveDutyShiftIdByAccount(opId, accountId)
        .orElseThrow(
            () -> new MarkerApiException("police_phone_not_assigned", HttpStatus.FORBIDDEN));
  }

  @Transactional(readOnly = true)
  public Marker requireUpdateAccess(UUID markerId, MarkerRequestContext context) {
    return requireMutationAccess(markerId, context);
  }

  @Transactional(readOnly = true)
  public Marker requireDeleteAccess(UUID markerId, MarkerRequestContext context) {
    return requireMutationAccess(markerId, context);
  }

  private Marker requireMutationAccess(UUID markerId, MarkerRequestContext context) {
    requireFieldOrWebContext(context);
    Marker marker =
        markerMapper
            .findById(markerId)
            .filter(saved -> !MarkerStatus.DELETED.name().equals(saved.getStatus()))
            .orElseThrow(
                () -> new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN));
    requireOpenIncident(marker.getIncidentId());
    requireAccountAssignment(marker.getIncidentId(), context.authentication().accountId());
    if ("WEB".equals(context.authentication().channel())) {
      requireWebReferenceMarker(marker);
    } else {
      requireAppOwnFieldMarker(marker, context);
    }
    return marker;
  }

  private void requireAppContext(MarkerRequestContext context) {
    if (context == null || context.authentication() == null) {
      throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
    if (!"APP".equals(context.authentication().channel())) {
      throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
  }

  private void requireFieldOrWebContext(MarkerRequestContext context) {
    if (context == null || context.authentication() == null) {
      throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
    String channel = context.authentication().channel();
    if ("APP".equals(channel)) {
      if (context.authentication().policePhoneId() == null) {
        throw new MarkerApiException("police_phone_required", HttpStatus.BAD_REQUEST);
      }
      return;
    }
    if ("WEB".equals(channel)) {
      return;
    }
    throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
  }

  private void requireWebReferenceMarker(Marker marker) {
    if (marker.isReferenceMarker()) {
      return;
    }
    throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }

  private void requireAppOwnFieldMarker(Marker marker, MarkerRequestContext context) {
    if (!marker.isFieldMarkerCreatedBy(context.authentication().accountId())) {
      throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
  }

  private void requireOpenIncident(UUID incidentId) {
    String status =
        markerAccessMapper
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

  private void requireCurrentOp(UUID incidentId, UUID opId) {
    UUID currentOpId =
        markerAccessMapper
            .findCurrentOpId(incidentId)
            .orElseThrow(() -> new MarkerApiException("op_required", HttpStatus.CONFLICT));
    if (!currentOpId.equals(opId)) {
      throw new MarkerApiException("op_mismatch", HttpStatus.CONFLICT);
    }
  }

  private void requireAccountAssignment(UUID incidentId, UUID accountId) {
    if (markerAccessMapper.countActiveAssignmentsByAccountId(accountId) == 0) {
      throw new MarkerApiException("team_not_assigned", HttpStatus.FORBIDDEN);
    }
    if (markerAccessMapper.countActiveIncidentAssignment(incidentId, accountId) == 0) {
      throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
  }
}
