package com.surimap.api.controller.dutyshift;

import com.surimap.api.controller.dutyshift.response.DutyShiftListResponse;
import com.surimap.api.service.dutyshift.DutyShiftQueryService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DutyShiftQueryController {

  private final DutyShiftQueryService service;

  public DutyShiftQueryController(DutyShiftQueryService service) {
    this.service = service;
  }

  @GetMapping("/api/duty-shifts")
  @RequireChannel({Channel.APP, Channel.WEB})
  @RequireIncidentAccess
  public ResponseEntity<DutyShiftListResponse> list(
      @RequestParam UUID incidentId,
      @RequestParam(required = false) UUID opId,
      @RequestParam(required = false) UUID policePhoneId,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok(service.list(incidentId, opId, policePhoneId, accountId, status));
  }
}
