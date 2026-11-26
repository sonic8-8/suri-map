package com.surimap.api.controller.dutyshift.response;

import com.surimap.dutyshift.DutyShift;
import java.time.Instant;
import java.util.UUID;

public record DutyShiftResponse(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID accountId,
    String accountDisplayName,
    String status,
    Instant startedAt,
    Instant endedAt,
    long version) {

  public static DutyShiftResponse from(DutyShift dutyShift) {
    return new DutyShiftResponse(
        dutyShift.getId(),
        dutyShift.getIncidentId(),
        dutyShift.getOpId(),
        dutyShift.getAccountId(),
        dutyShift.getAccountDisplayName(),
        dutyShift.getStatus(),
        dutyShift.getStartedAt(),
        dutyShift.getEndedAt(),
        dutyShift.getVersion());
  }
}
