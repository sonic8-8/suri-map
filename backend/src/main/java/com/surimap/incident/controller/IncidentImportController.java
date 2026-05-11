package com.surimap.incident.controller;

import com.surimap.incident.controller.request.ImportIncidentRequest;
import com.surimap.incident.controller.response.ImportIncidentResponse;
import com.surimap.incident.service.IncidentImportCommand;
import com.surimap.incident.service.IncidentImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentImportController {

  private final IncidentImportService incidentImportService;

  public IncidentImportController(IncidentImportService incidentImportService) {
    this.incidentImportService = incidentImportService;
  }

  @PostMapping("/import")
  public ResponseEntity<ImportIncidentResponse> importIncident(
      @RequestHeader(value = "X-Client-Channel", required = false) String clientChannel,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ImportIncidentRequest request) {
    var result =
        incidentImportService.importIncident(
            new IncidentImportCommand(
                request.sourceIncidentId(),
                idempotencyKey,
                clientChannel,
                SecurityContextHolder.getContext().getAuthentication()));

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            new ImportIncidentResponse(
                result.id(),
                result.incidentId(),
                result.status(),
                result.version(),
                result.assignmentAccountIds()));
  }
}
