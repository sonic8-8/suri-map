package com.surimap.app.controller.policephone.request;

import jakarta.validation.constraints.NotBlank;

public class FcmTokenRequest {

  @NotBlank private String appInstanceId;
  @NotBlank private String token;

  public String getAppInstanceId() {
    return appInstanceId;
  }

  public void setAppInstanceId(String appInstanceId) {
    this.appInstanceId = appInstanceId;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }
}
