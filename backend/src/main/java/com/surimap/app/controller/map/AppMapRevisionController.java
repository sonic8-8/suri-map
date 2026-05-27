package com.surimap.app.controller.map;

import com.surimap.app.controller.map.response.AppMapRevisionResponse;
import com.surimap.app.service.map.AppMapRevisionQueryService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.retention.purge.RecordLocationAccess;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents/{incidentId}/map-revisions")
public class AppMapRevisionController {

  private final AppMapRevisionQueryService service;

  public AppMapRevisionController(AppMapRevisionQueryService service) {
    this.service = service;
  }

  @GetMapping
  @RequireChannel(Channel.APP)
  @RequireIncidentAccess
  @RecordLocationAccess(accessPurpose = "MAP_REVISION_READ")
  public ResponseEntity<AppMapRevisionResponse> revisions(
      @PathVariable UUID incidentId,
      @RequestParam(required = false) UUID opId,
      @RequestParam(required = false) UUID policePhoneId) {
    return ResponseEntity.ok(service.revisions(incidentId, opId, policePhoneId));
  }
}
