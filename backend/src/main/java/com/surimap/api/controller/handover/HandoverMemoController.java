package com.surimap.api.controller.handover;

import com.surimap.api.controller.handover.request.CreateHandoverMemoRequest;
import com.surimap.api.controller.handover.response.HandoverMemoListResponse;
import com.surimap.api.controller.handover.response.HandoverMemoResponse;
import com.surimap.api.service.handover.HandoverMemoApiService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneAssigned;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.common.auth.SuriMapAuthentication;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HandoverMemoController {

  private final HandoverMemoApiService service;

  public HandoverMemoController(HandoverMemoApiService service) {
    this.service = service;
  }

  @PostMapping("/api/handover-memos")
  @RequireChannel({Channel.APP, Channel.WEB})
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  @RequirePolicePhoneAssigned
  @RequireIncidentAccess
  public ResponseEntity<HandoverMemoResponse> create(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateHandoverMemoRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.create(request, idempotencyKey, actorAccountId()));
  }

  @GetMapping("/api/handover-memos")
  @RequireChannel({Channel.APP, Channel.WEB})
  @RequireIncidentAccess
  public ResponseEntity<HandoverMemoListResponse> list(
      @RequestParam UUID incidentId,
      @RequestParam(required = false) UUID opId,
      @RequestParam(required = false) String memoTargetType,
      @RequestParam(required = false) UUID memoTargetId) {
    return ResponseEntity.ok(service.list(incidentId, opId, memoTargetType, memoTargetId));
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
