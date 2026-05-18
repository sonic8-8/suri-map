package com.surimap.api.controller.handover;

import com.surimap.api.controller.handover.response.HandoverTimelineResponse;
import com.surimap.api.service.handover.HandoverTimelineApiService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HandoverTimelineController {

  private final HandoverTimelineApiService service;

  public HandoverTimelineController(HandoverTimelineApiService service) {
    this.service = service;
  }

  @GetMapping("/api/operational-periods/{operationalPeriodId}/handover-timeline")
  @RequireChannel({Channel.APP, Channel.WEB})
  @RequireIncidentAccess
  public ResponseEntity<HandoverTimelineResponse> get(
      @PathVariable UUID operationalPeriodId,
      @RequestParam UUID incidentId,
      @RequestParam(required = false) String scopeType,
      @RequestParam(required = false) UUID dutyShiftId,
      @RequestParam(required = false) Instant startAt,
      @RequestParam(required = false) Instant endAt,
      @RequestParam(required = false) Boolean includeOtherActors) {
    return ResponseEntity.ok(
        service.get(
            incidentId,
            operationalPeriodId,
            scopeType,
            dutyShiftId,
            startAt,
            endAt,
            includeOtherActors));
  }
}
