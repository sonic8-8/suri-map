package com.surimap.api.controller.dutyshift.response;

import com.surimap.dutyshift.DutyShift;
import java.util.UUID;

public record DutyShiftResponse(
    UUID id, UUID incidentId, UUID opId, UUID policePhoneId, String status, long version) {

  public static DutyShiftResponse from(DutyShift dutyShift) {
    return new DutyShiftResponse(
        dutyShift.getId(),
        dutyShift.getIncidentId(),
        dutyShift.getOpId(),
        dutyShift.getPolicePhoneId(),
        dutyShift.getStatus(),
        dutyShift.getVersion());
  }
}
