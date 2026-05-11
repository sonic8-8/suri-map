package com.surimap.account.controller.request;

public class AuthLogoutRequest {

  private String sessionId;

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
}
