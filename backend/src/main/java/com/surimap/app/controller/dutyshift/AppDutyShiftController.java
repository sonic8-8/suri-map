package com.surimap.app.controller.dutyshift;

import com.surimap.api.controller.dutyshift.response.DutyShiftEndResponse;
import com.surimap.api.controller.dutyshift.response.DutyShiftResponse;
import com.surimap.app.controller.dutyshift.request.EndDutyShiftRequest;
import com.surimap.app.controller.dutyshift.request.StartDutyShiftRequest;
import com.surimap.app.service.dutyshift.AppDutyShiftCommandService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.SuriMapAuthentication;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppDutyShiftController {

  private final AppDutyShiftCommandService service;

  public AppDutyShiftController(AppDutyShiftCommandService service) {
    this.service = service;
  }

  @PostMapping("/api/duty-shifts")
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequireIncidentAccess
  public ResponseEntity<DutyShiftResponse> start(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody StartDutyShiftRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.start(request, idempotencyKey, actorAccountId()));
  }

  @PatchMapping("/api/duty-shifts/{dutyShiftId}")
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequireIncidentAccess
  public ResponseEntity<DutyShiftEndResponse> end(
      @PathVariable UUID dutyShiftId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody EndDutyShiftRequest request) {
    return ResponseEntity.ok(
        service.end(dutyShiftId, request, idempotencyKey, actorAccountId()));
  }

  private UUID actorAccountId() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof SuriMapAuthentication suriMapAuthentication) {
      try {
        return UUID.fromString(suriMapAuthentication.getAccountId());
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
    return null;
  }
}
