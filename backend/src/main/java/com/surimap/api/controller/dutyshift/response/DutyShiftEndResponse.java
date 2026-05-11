package com.surimap.api.controller.dutyshift.response;

import com.surimap.dutyshift.DutyShift;
import java.time.Instant;
import java.util.UUID;

public record DutyShiftEndResponse(UUID id, String status, long version, Instant endedAt) {

  public static DutyShiftEndResponse from(DutyShift dutyShift) {
    return new DutyShiftEndResponse(
        dutyShift.getId(), dutyShift.getStatus(), dutyShift.getVersion(), dutyShift.getEndedAt());
  }
}
