package com.surimap.app.controller.policephone;

import com.surimap.app.controller.policephone.request.PolicePhoneHeartbeatRequest;
import com.surimap.app.controller.policephone.response.PolicePhoneHeartbeatResponse;
import com.surimap.app.service.policephone.AppPolicePhoneHeartbeatService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.auth.guard.ChannelNotAllowedException;
import com.surimap.common.auth.guard.PolicePhoneRequiredException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PolicePhoneHeartbeatController {

  private final AppPolicePhoneHeartbeatService service;

  public PolicePhoneHeartbeatController(AppPolicePhoneHeartbeatService service) {
    this.service = service;
  }

  @PostMapping("/api/police-phones/{policePhoneId}/heartbeat")
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  public ResponseEntity<PolicePhoneHeartbeatResponse> heartbeat(
      @PathVariable UUID policePhoneId,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneIdHeader,
      @Valid @RequestBody PolicePhoneHeartbeatRequest request) {
    // ONLINE response includes policePhoneId, sequence, lastHeartbeatAt, and lastSyncAt.
    SuriMapAuthentication auth = currentAuthentication();
    validatePolicePhoneBinding(policePhoneId, policePhoneIdHeader, auth);
    return ResponseEntity.ok(
        PolicePhoneHeartbeatResponse.from(
            service.heartbeat(
                request.toServiceRequest(
                    policePhoneId,
                    auth.getAccountId(),
                    auth.getAccountType(),
                    auth.getOrganizationType()))));
  }

  private SuriMapAuthentication currentAuthentication() {
    var current = SecurityContextHolder.getContext().getAuthentication();
    if (current instanceof SuriMapAuthentication auth) {
      return auth;
    }
    throw new ChannelNotAllowedException();
  }

  private void validatePolicePhoneBinding(
      UUID policePhoneId, String policePhoneIdHeader, SuriMapAuthentication auth) {
    if (policePhoneIdHeader == null || policePhoneIdHeader.isBlank()) {
      throw new PolicePhoneRequiredException();
    }

    UUID headerId;
    UUID sessionId;
    try {
      headerId = UUID.fromString(policePhoneIdHeader);
      sessionId = UUID.fromString(auth.getPolicePhoneId());
    } catch (Exception ignored) {
      throw new PolicePhoneRequiredException();
    }

    if (!policePhoneId.equals(headerId) || !policePhoneId.equals(sessionId)) {
      throw new PolicePhoneRequiredException();
    }
  }
}
