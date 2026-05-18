package com.surimap.marker.photo.adapter;

import com.surimap.marker.adapter.MarkerRuntimeGuardMapper;
import com.surimap.marker.photo.domain.PhotoMarkerContext;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.port.PhotoWriteGuardPort;
import com.surimap.marker.photo.service.PhotoRequestContext;
import com.surimap.marker.repository.MarkerRecord;
import com.surimap.marker.repository.MarkerRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Runtime S5 photo write guard for APP marker photo upload-url and attach APIs. */
@Component
public class RuntimePhotoWriteGuardAdapter implements PhotoWriteGuardPort {

  private final MarkerRepository markerRepository;
  private final MarkerRuntimeGuardMapper markerRuntimeGuardMapper;

  public RuntimePhotoWriteGuardAdapter(
      MarkerRepository markerRepository, MarkerRuntimeGuardMapper markerRuntimeGuardMapper) {
    this.markerRepository = Objects.requireNonNull(markerRepository);
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
    MarkerRecord marker = findActiveMarker(markerId);
    UUID accountId = context.authentication().accountId();
    UUID policePhoneId = context.authentication().policePhoneId();

    requireOpenIncident(marker.getIncidentId());
    requireAccountAssignment(marker.getIncidentId(), accountId);
    requireRegisteredPolicePhone(policePhoneId);
    requireCurrentOp(marker);

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

  private MarkerRecord findActiveMarker(UUID markerId) {
    if (markerId == null) {
      throw conflict();
    }
    MarkerRecord marker =
        markerRepository.findById(markerId).orElseThrow(RuntimePhotoWriteGuardAdapter::conflict);
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

  private void requireRegisteredPolicePhone(UUID policePhoneId) {
    if (policePhoneId == null
        || markerRuntimeGuardMapper.countRegisteredPolicePhone(policePhoneId) == 0) {
      throw new PhotoApiException("police_phone_not_registered", HttpStatus.FORBIDDEN);
    }
  }

  private void requireCurrentOp(MarkerRecord marker) {
    UUID currentOpId =
        markerRuntimeGuardMapper
            .findCurrentOpId(marker.getIncidentId())
            .orElseThrow(() -> new PhotoApiException("op_required", HttpStatus.CONFLICT));
    if (!currentOpId.equals(marker.getOperationalPeriodId())) {
      throw new PhotoApiException("op_mismatch", HttpStatus.CONFLICT);
    }
  }

  private static PhotoApiException denied() {
    return new PhotoApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }

  private static PhotoApiException conflict() {
    return new PhotoApiException("write_conflict", HttpStatus.CONFLICT);
  }
}
