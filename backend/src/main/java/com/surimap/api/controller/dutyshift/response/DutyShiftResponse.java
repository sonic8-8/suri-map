package com.surimap.api.controller.dutyshift.response;

import com.surimap.dutyshift.DutyShift;
import java.time.Instant;
import java.util.UUID;

public record DutyShiftResponse(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID policePhoneId,
    String policePhoneLabel,
    String status,
    Instant startedAt,
    Instant endedAt,
    long version) {

  public static DutyShiftResponse from(DutyShift dutyShift) {
    return new DutyShiftResponse(
        dutyShift.getId(),
        dutyShift.getIncidentId(),
        dutyShift.getOpId(),
        dutyShift.getPolicePhoneId(),
        policePhoneLabel(dutyShift),
        dutyShift.getStatus(),
        dutyShift.getStartedAt(),
        dutyShift.getEndedAt(),
        dutyShift.getVersion());
  }

  private static String policePhoneLabel(DutyShift dutyShift) {
    if (hasText(dutyShift.getPolicePhoneDisplayName())) {
      return dutyShift.getPolicePhoneDisplayName();
    }
    if (hasText(dutyShift.getPolicePhoneCode())) {
      return dutyShift.getPolicePhoneCode();
    }
    return dutyShift.getPolicePhoneId() == null ? null : dutyShift.getPolicePhoneId().toString();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
