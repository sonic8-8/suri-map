package com.surimap.marker.photo.adapter;

import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.marker.adapter.MarkerRuntimeGuardMapper;
import com.surimap.marker.photo.domain.PhotoMarkerContext;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.port.PhotoWriteGuardPort;
import com.surimap.marker.photo.service.PhotoRequestContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Runtime S5 photo write guard for APP marker photo upload-url and attach APIs. */
@Component
public class RuntimePhotoWriteGuardAdapter implements PhotoWriteGuardPort {

  private final MarkerMapper markerMapper;
  private final MarkerRuntimeGuardMapper markerRuntimeGuardMapper;

  public RuntimePhotoWriteGuardAdapter(
      MarkerMapper markerMapper, MarkerRuntimeGuardMapper markerRuntimeGuardMapper) {
    this.markerMapper = Objects.requireNonNull(markerMapper);
    this.markerRuntimeGuardMapper = Objects.requireNonNull(markerRuntimeGuardMapper);
  }

  @Override
  @Transactional(readOnly = true)
  public PhotoMarkerContext requireUploadUrlAccess(UUID markerId, PhotoRequestContext context) {
    return requirePhotoAccess(markerId, context);
  }

  @Override
  @Transactional(readOnly = true)
  public PhotoMarkerContext requireAttachAccess(
      UUID markerId, UUID photoId, PhotoRequestContext context) {
    if (photoId == null) {
      throw conflict();
    }
    return requirePhotoAccess(markerId, context);
  }

  private PhotoMarkerContext requirePhotoAccess(UUID markerId, PhotoRequestContext context) {
    requireAppContext(context);
    Marker marker = findActiveMarker(markerId);
    UUID accountId = context.authentication().accountId();
    UUID policePhoneId = context.authentication().policePhoneId();

    requireOpenIncident(marker.getIncidentId());
    requireAccountAssignment(marker.getIncidentId(), accountId);
    requireCurrentOp(marker);
    requireActiveDutyShift(marker.getOperationalPeriodId(), accountId);
    requireAppOwnFieldMarker(marker, accountId);

    return new PhotoMarkerContext(
        marker.getIncidentId(),
        marker.getId(),
        marker.getOperationalPeriodId(),
        policePhoneId,
        marker.getStatus(),
        marker.getVersion());
  }

  private void requireAppContext(PhotoRequestContext context) {
    if (context == null || context.authentication() == null) {
      throw denied();
    }
    if (!"APP".equals(context.authentication().channel())) {
      throw new PhotoApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
  }

  private Marker findActiveMarker(UUID markerId) {
    if (markerId == null) {
      throw conflict();
    }
    Marker marker =
        markerMapper.findById(markerId).orElseThrow(RuntimePhotoWriteGuardAdapter::conflict);
    if ("DELETED".equals(marker.getStatus())) {
      throw conflict();
    }
    return marker;
  }

  private void requireOpenIncident(UUID incidentId) {
    String status =
        markerRuntimeGuardMapper
            .findIncidentStatus(incidentId)
            .orElseThrow(RuntimePhotoWriteGuardAdapter::denied);
    if ("OPEN".equals(status)) {
      return;
    }
    if ("CLOSED".equals(status)) {
      throw new PhotoApiException("incident_closed", HttpStatus.CONFLICT);
    }
    throw denied();
  }

  private void requireAccountAssignment(UUID incidentId, UUID accountId) {
    if (markerRuntimeGuardMapper.countActiveAssignmentsByAccountId(accountId) == 0) {
      throw new PhotoApiException("team_not_assigned", HttpStatus.FORBIDDEN);
    }
    if (markerRuntimeGuardMapper.countActiveIncidentAssignment(incidentId, accountId) == 0) {
      throw denied();
    }
  }

  private void requireCurrentOp(Marker marker) {
    UUID currentOpId =
        markerRuntimeGuardMapper
            .findCurrentOpId(marker.getIncidentId())
            .orElseThrow(() -> new PhotoApiException("op_required", HttpStatus.CONFLICT));
    if (!currentOpId.equals(marker.getOperationalPeriodId())) {
      throw new PhotoApiException("op_mismatch", HttpStatus.CONFLICT);
    }
  }

  private void requireActiveDutyShift(UUID opId, UUID accountId) {
    markerRuntimeGuardMapper
        .findActiveDutyShiftIdByAccount(opId, accountId)
        .orElseThrow(
            () -> new PhotoApiException("police_phone_not_assigned", HttpStatus.FORBIDDEN));
  }

  private void requireAppOwnFieldMarker(Marker marker, UUID accountId) {
    if (!"APP".equals(marker.getMarkerSource())
        || !accountId.equals(marker.getCreatedByAccountId())) {
      throw denied();
    }
  }

  private static PhotoApiException denied() {
    return new PhotoApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }

  private static PhotoApiException conflict() {
    return new PhotoApiException("write_conflict", HttpStatus.CONFLICT);
  }
}
