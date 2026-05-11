package com.surimap.account.controller.request;

import com.surimap.account.service.AuthLoginCommand;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.guard.ChannelNotAllowedException;
import jakarta.validation.constraints.NotBlank;

public class AuthLoginRequest {

  @NotBlank private String accountCode;
  @NotBlank private String password;
  @NotBlank private String channel;
  private String policePhoneCode;

  public String getAccountCode() {
    return accountCode;
  }

  public void setAccountCode(String accountCode) {
    this.accountCode = accountCode;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getPolicePhoneCode() {
    return policePhoneCode;
  }

  public void setPolicePhoneCode(String policePhoneCode) {
    this.policePhoneCode = policePhoneCode;
  }

  public AuthLoginCommand toCommand(String headerChannel) {
    Channel parsedHeader = parseChannel(headerChannel);
    Channel parsedBody = parseChannel(channel);
    if (parsedHeader != parsedBody || parsedBody == Channel.INTERNAL) {
      throw new ChannelNotAllowedException();
    }
    return new AuthLoginCommand(accountCode, password, parsedBody, policePhoneCode);
  }

  private static Channel parseChannel(String value) {
    if (value == null || value.isBlank()) {
      throw new ChannelNotAllowedException();
    }
    try {
      return Channel.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new ChannelNotAllowedException();
    }
  }
}
