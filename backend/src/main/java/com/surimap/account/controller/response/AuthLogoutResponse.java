package com.surimap.account.controller.response;

public class AuthLogoutResponse {

  private final String status;

  public AuthLogoutResponse(String status) {
    this.status = status;
  }

  public String getStatus() {
    return status;
  }
}
