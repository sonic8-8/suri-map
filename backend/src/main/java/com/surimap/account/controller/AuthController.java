package com.surimap.account.controller;

import com.surimap.account.controller.request.AuthLoginRequest;
import com.surimap.account.controller.request.AuthLogoutRequest;
import com.surimap.account.controller.response.AuthLoginResponse;
import com.surimap.account.controller.response.AuthLogoutResponse;
import com.surimap.account.service.AuthSessionService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

  private final AuthSessionService authSessionService;

  public AuthController(AuthSessionService authSessionService) {
    this.authSessionService = authSessionService;
  }

  @PostMapping("/api/auth/login")
  public ResponseEntity<AuthLoginResponse> login(
      @RequestHeader("X-Client-Channel") String channel,
      @Valid @RequestBody AuthLoginRequest request) {
    return ResponseEntity.ok(
        AuthLoginResponse.from(authSessionService.login(request.toCommand(channel))));
  }

  @PostMapping("/api/auth/logout")
  @RequireChannel({Channel.APP, Channel.WEB})
  public ResponseEntity<AuthLogoutResponse> logout(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestBody(required = false) AuthLogoutRequest request) {
    authSessionService.logout(
        accessToken(authorization), request == null ? null : request.getSessionId());
    return ResponseEntity.ok(new AuthLogoutResponse("LOGGED_OUT"));
  }

  private static String accessToken(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      return null;
    }
    String value = authorization.trim();
    if (value.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
      return value.substring("Bearer ".length()).trim();
    }
    return null;
  }
}
