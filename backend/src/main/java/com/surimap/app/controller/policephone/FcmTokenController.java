package com.surimap.app.controller.policephone;

import com.surimap.app.controller.policephone.request.FcmTokenRequest;
import com.surimap.app.controller.policephone.response.FcmTokenResponse;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.auth.guard.ChannelNotAllowedException;
import com.surimap.common.auth.guard.PolicePhoneRequiredException;
import com.surimap.policephone.PolicePhonePersistenceService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FcmTokenController {

  private final PolicePhonePersistenceService policePhonePersistenceService;

  public FcmTokenController(PolicePhonePersistenceService policePhonePersistenceService) {
    this.policePhonePersistenceService = policePhonePersistenceService;
  }

  @PostMapping("/api/fcm/tokens")
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  public ResponseEntity<FcmTokenResponse> register(
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneIdHeader,
      @Valid @RequestBody FcmTokenRequest request) {
    SuriMapAuthentication auth = currentAuthentication();
    UUID policePhoneId = validatePolicePhoneBinding(policePhoneIdHeader, auth);
    return ResponseEntity.ok(
        FcmTokenResponse.from(
            policePhonePersistenceService.registerFcmToken(
                policePhoneId, auth.getAccountId(), request.getAppInstanceId(), request.getToken())));
  }

  private SuriMapAuthentication currentAuthentication() {
    var current = SecurityContextHolder.getContext().getAuthentication();
    if (current instanceof SuriMapAuthentication auth) {
      return auth;
    }
    throw new ChannelNotAllowedException();
  }

  private UUID validatePolicePhoneBinding(String policePhoneIdHeader, SuriMapAuthentication auth) {
    if (policePhoneIdHeader == null || policePhoneIdHeader.isBlank()) {
      throw new PolicePhoneRequiredException();
    }
    try {
      UUID headerId = UUID.fromString(policePhoneIdHeader);
      UUID sessionId = UUID.fromString(auth.getPolicePhoneId());
      if (headerId.equals(sessionId)) {
        return headerId;
      }
    } catch (Exception ignored) {
      throw new PolicePhoneRequiredException();
    }
    throw new PolicePhoneRequiredException();
  }
}
