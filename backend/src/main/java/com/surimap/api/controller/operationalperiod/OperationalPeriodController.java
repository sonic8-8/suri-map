package com.surimap.api.controller.operationalperiod;

import com.surimap.api.controller.operationalperiod.request.CreateOperationalPeriodRequest;
import com.surimap.api.controller.operationalperiod.response.OperationalPeriodListResponse;
import com.surimap.api.controller.operationalperiod.response.OperationalPeriodResponse;
import com.surimap.api.service.operationalperiod.OperationalPeriodApiService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperationalPeriodController {

  private final OperationalPeriodApiService operationalPeriodApiService;

  public OperationalPeriodController(OperationalPeriodApiService operationalPeriodApiService) {
    this.operationalPeriodApiService = operationalPeriodApiService;
  }

  @PostMapping("/api/operational-periods")
  @RequireChannel(Channel.WEB)
  @RequireRole
  @RequireIncidentAccess
  public ResponseEntity<OperationalPeriodResponse> create(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateOperationalPeriodRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(operationalPeriodApiService.create(request, idempotencyKey, actorAccountId()));
  }

  @GetMapping("/api/incidents/{incidentId}/operational-periods")
  @RequireChannel({Channel.APP, Channel.WEB})
  @RequireIncidentAccess
  public ResponseEntity<OperationalPeriodListResponse> list(@PathVariable UUID incidentId) {
    return ResponseEntity.ok(operationalPeriodApiService.list(incidentId));
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
