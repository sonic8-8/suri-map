package com.surimap.app.controller.map;

import com.surimap.app.controller.map.response.AppMapRevisionResponse;
import com.surimap.app.service.map.AppMapRevisionQueryService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.retention.purge.RecordLocationAccess;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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
      // 쓰지 않는 값이다. 형식 검사까지 함께 없애는 일은 #62에서 한다.
      @RequestParam(required = false) UUID policePhoneId) {
    return ResponseEntity.ok(service.revisions(incidentId, opId, currentAccountId()));
  }

  // 계정 ID가 UUID가 아니면 배정된 구역이 있을 수 없으므로, 배정이 없는 revision이 계산된다.
  private static UUID currentAccountId() {
    if (SecurityContextHolder.getContext().getAuthentication()
        instanceof SuriMapAuthentication authentication) {
      try {
        return UUID.fromString(authentication.getAccountId());
      } catch (IllegalArgumentException exception) {
        return null;
      }
    }
    return null;
  }
}
