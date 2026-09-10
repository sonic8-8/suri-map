package com.surimap.marker.adapter;

import com.surimap.domain.marker.MarkerAccessMapper;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.service.MarkerRequestContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 사건 상태와 배정 정보를 조회해 마커 기록 권한의 공통 조건을 검사한다. */
@Component
public class RuntimeMarkerWriteGuardAdapter {

  private final MarkerAccessMapper markerAccessMapper;

  public RuntimeMarkerWriteGuardAdapter(MarkerAccessMapper markerAccessMapper) {
    this.markerAccessMapper = markerAccessMapper;
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
  public void requireIncidentAccess(UUID incidentId, UUID accountId) {
    requireOpenIncident(incidentId);
    requireAccountAssignment(incidentId, accountId);
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
