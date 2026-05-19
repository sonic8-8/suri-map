package com.surimap.api.controller.opcomparison;

import com.surimap.api.controller.opcomparison.request.CreateOpComparisonRequest;
import com.surimap.api.controller.opcomparison.response.OpComparisonResponse;
import com.surimap.api.service.opcomparison.OpComparisonApiService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.RequireRole;
import com.surimap.common.auth.SuriMapAuthentication;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpComparisonController {

  private final OpComparisonApiService service;

  public OpComparisonController(OpComparisonApiService service) {
    this.service = service;
  }

  @PostMapping("/api/operational-periods/comparisons")
  @RequireChannel(Channel.WEB)
  @RequireRole
  @RequireIncidentAccess
  public ResponseEntity<OpComparisonResponse> create(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateOpComparisonRequest request) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(service.create(request, idempotencyKey, actorAccountId()));
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
