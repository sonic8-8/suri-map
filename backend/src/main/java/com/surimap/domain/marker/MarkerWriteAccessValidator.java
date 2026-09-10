package com.surimap.domain.marker;

import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Service가 조회한 값으로 공통 권한 조건을 검사한다. DB 조회와 마커 변경은 하지 않는다. */
@Component
public class MarkerWriteAccessValidator {

  public void validateCreateAccess(MarkerWriteAccessData accessData, UUID requestedOpId) {
    validateOpenIncident(accessData.getIncidentStatus());
    validateCurrentOp(accessData.getCurrentOpId(), requestedOpId);
    validateAccountAssignment(accessData);
    validateActiveDutyShift(accessData.getActiveDutyShiftId());
  }

  public void validateUploadBeforeMarkerCreationAccess(
      MarkerWriteAccessData accessData, UUID requestedOpId) {
    // 생성 전 사진 업로드는 마커 생성과 달리, 현재 수색 차수를 사건 상태보다 먼저 검사한다.
    validateCurrentOp(accessData.getCurrentOpId(), requestedOpId);
    validateOpenIncident(accessData.getIncidentStatus());
    validateAccountAssignment(accessData);
    validateActiveDutyShift(accessData.getActiveDutyShiftId());
  }

  public void validateIncidentAccess(MarkerWriteAccessData accessData) {
    validateOpenIncident(accessData.getIncidentStatus());
    validateAccountAssignment(accessData);
  }

  public void validatePhotoAccess(MarkerWriteAccessData accessData, UUID markerOpId) {
    // 기존 마커의 사진은 생성과 달리, 배정 여부를 현재 수색 차수보다 먼저 검사한다.
    validateIncidentAccess(accessData);
    validateCurrentOp(accessData.getCurrentOpId(), markerOpId);
    validateActiveDutyShift(accessData.getActiveDutyShiftId());
  }

  private void validateOpenIncident(String status) {
    if ("OPEN".equals(status)) {
      return;
    }
    if ("CLOSED".equals(status)) {
      throw new BusinessException(ErrorCode.INCIDENT_CLOSED);
    }
    throw new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED);
  }

  private void validateCurrentOp(UUID currentOpId, UUID requestedOpId) {
    if (requestedOpId == null || currentOpId == null) {
      throw new BusinessException(ErrorCode.OP_REQUIRED);
    }
    if (!currentOpId.equals(requestedOpId)) {
      throw new BusinessException(ErrorCode.OP_MISMATCH);
    }
  }

  private void validateAccountAssignment(MarkerWriteAccessData accessData) {
    if (accessData.getActiveAssignmentCount() == 0) {
      throw new BusinessException(ErrorCode.TEAM_NOT_ASSIGNED);
    }
    if (accessData.getActiveIncidentAssignmentCount() == 0) {
      throw new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED);
    }
  }

  private void validateActiveDutyShift(UUID dutyShiftId) {
    if (dutyShiftId == null) {
      throw new BusinessException(ErrorCode.POLICE_PHONE_NOT_ASSIGNED);
    }
  }
}
