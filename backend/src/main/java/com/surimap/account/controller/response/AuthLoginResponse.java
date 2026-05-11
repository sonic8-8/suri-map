package com.surimap.account.controller.response;

import com.surimap.account.service.AuthLoginResult;
import java.util.UUID;

public class AuthLoginResponse {

  private final UUID sessionId;
  private final String accessToken;
  private final SecurityContextResponse securityContext;

  private AuthLoginResponse(
      UUID sessionId, String accessToken, SecurityContextResponse securityContext) {
    this.sessionId = sessionId;
    this.accessToken = accessToken;
    this.securityContext = securityContext;
  }

  public static AuthLoginResponse from(AuthLoginResult result) {
    return new AuthLoginResponse(
        result.sessionId(),
        result.accessToken(),
        SecurityContextResponse.from(result.securityContext()));
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public SecurityContextResponse getSecurityContext() {
    return securityContext;
  }
}
