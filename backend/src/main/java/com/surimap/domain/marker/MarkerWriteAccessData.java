package com.surimap.domain.marker;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 마커 권한 검사를 위해 조회한 사건·배정·근무교대 정보다. 허용 여부는 Validator가 판단한다. */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MarkerWriteAccessData {

  private String incidentStatus;
  private UUID currentOpId;
  private int activeAssignmentCount;
  private int activeIncidentAssignmentCount;
  private UUID activeDutyShiftId;
}
