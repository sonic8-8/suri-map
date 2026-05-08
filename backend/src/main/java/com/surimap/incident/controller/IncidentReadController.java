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

/** S1-1 active 사건 목록·상세 public read endpoint. */
@RestController
@RequestMapping("/incidents")
public class IncidentReadController {

  private final IncidentReadQueryService incidentReadQueryService;

  public IncidentReadController(IncidentReadQueryService incidentReadQueryService) {
    this.incidentReadQueryService = incidentReadQueryService;
  }

  @GetMapping
  @RequireChannel({Channel.APP, Channel.WEB})
  public IncidentListResponse listActiveIncidents(
      @RequestParam(value = "status", required = false) String status) {
    var auth = currentAuthentication();
    return IncidentListResponse.from(
        incidentReadQueryService.findActiveIncidents(auth.getAccountId(), status));
  }

  @GetMapping("/{incidentId}")
  @RequireChannel({Channel.APP, Channel.WEB})
  @RequireIncidentAccess
  public ResponseEntity<IncidentDetailResponse> getActiveIncident(@PathVariable UUID incidentId) {
    var auth = currentAuthentication();
    // L1-T05A는 진행 중 사건 조회만 담당한다. 접근 범위는 service SQL의 active assignment 필터로 좁힌다.
    return incidentReadQueryService
        .findActiveIncidentDetail(incidentId, auth.getAccountId())
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
