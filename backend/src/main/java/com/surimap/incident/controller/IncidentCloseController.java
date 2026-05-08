package com.surimap.incident.controller;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.RequireRole;
import com.surimap.incident.controller.request.CloseIncidentRequest;
import com.surimap.incident.controller.response.CloseIncidentResponse;
import com.surimap.incident.service.IncidentCloseCommand;
import com.surimap.incident.service.IncidentCloseService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/incidents")
public class IncidentCloseController {

  private final IncidentCloseService incidentCloseService;

  public IncidentCloseController(IncidentCloseService incidentCloseService) {
    this.incidentCloseService = incidentCloseService;
  }

  @PostMapping("/{incidentId}/close")
  @RequireChannel({Channel.WEB})
  @RequireRole
  @RequireIncidentAccess
  public ResponseEntity<CloseIncidentResponse> closeIncident(
      @PathVariable UUID incidentId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CloseIncidentRequest request) {
    var result =
        incidentCloseService.closeIncident(
            new IncidentCloseCommand(
                incidentId,
                idempotencyKey,
                request.closeReason(),
                request.confirmPersonalDataRemoval(),
                SecurityContextHolder.getContext().getAuthentication()));

    return ResponseEntity.ok(CloseIncidentResponse.from(result));
  }
}
