package com.surimap.app.controller.sync;

import com.surimap.app.controller.sync.request.SyncHeartbeatRequest;
import com.surimap.app.controller.sync.response.SyncHeartbeatResponse;
import com.surimap.app.service.sync.AppSyncHeartbeatService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.auth.guard.ChannelNotAllowedException;
import com.surimap.common.auth.guard.PolicePhoneRequiredException;
import com.surimap.global.auth.PolicePhoneIdFormat;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SyncHeartbeatController {

  private final AppSyncHeartbeatService service;

  public SyncHeartbeatController(AppSyncHeartbeatService service) {
    this.service = service;
  }

  @PostMapping("/api/sync/heartbeat")
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  public ResponseEntity<SyncHeartbeatResponse> heartbeat(
      @Valid @RequestBody SyncHeartbeatRequest request) {
    SuriMapAuthentication auth = currentAuthentication();
    UUID policePhoneId =
        PolicePhoneIdFormat.parse(auth.getPolicePhoneId())
            .orElseThrow(PolicePhoneRequiredException::new);
    return ResponseEntity.ok(
        SyncHeartbeatResponse.from(
            service.heartbeat(
                request.toServiceRequest(UUID.fromString(auth.getAccountId()), policePhoneId))));
  }

  private SuriMapAuthentication currentAuthentication() {
    if (SecurityContextHolder.getContext().getAuthentication() instanceof SuriMapAuthentication auth) {
      return auth;
    }
    throw new ChannelNotAllowedException();
  }
}
