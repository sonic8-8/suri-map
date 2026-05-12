package com.surimap.incident.controller;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.incident.controller.response.IncidentDetailResponse;
import com.surimap.incident.controller.response.IncidentListResponse;
import com.surimap.incident.service.IncidentReadQueryService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** S1-1 사건 목록·상세 public read endpoint. 목록은 active 전용이고 상세는 terminal CLOSED도 노출한다. */
@RestController
@RequestMapping("/api/incidents")
public class IncidentReadController {

  private final IncidentReadQueryService incidentReadQueryService;

  public IncidentReadController(IncidentReadQueryService incidentReadQueryService) {
    this.incidentReadQueryService = incidentReadQueryService;
  }

  @GetMapping
  @RequireChannel({Channel.APP, Channel.WEB})
  public ResponseEntity<IncidentListResponse> listActiveIncidents(
      @RequestParam(value = "status", required = false) String status) {
    var auth = currentAuthentication();
    return ResponseEntity.ok(
        IncidentListResponse.from(
            incidentReadQueryService.findActiveIncidents(UUID.fromString(auth.getAccountId()), status)));
  }

  @GetMapping("/{incidentId}")
  @RequireChannel({Channel.APP, Channel.WEB})
  @RequireIncidentAccess
  public ResponseEntity<IncidentDetailResponse> getIncidentDetail(@PathVariable UUID incidentId) {
    var auth = currentAuthentication();
    // 접근 범위는 service SQL의 incident_assignment 필터로 좁히고, CLOSED는 sanitized DTO만 반환한다.
    return incidentReadQueryService
        .findIncidentDetail(incidentId, UUID.fromString(auth.getAccountId()))
        .map(IncidentDetailResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private SuriMapAuthentication currentAuthentication() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof SuriMapAuthentication suriMapAuthentication) {
      return suriMapAuthentication;
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }
}
