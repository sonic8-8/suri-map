package com.surimap.account.controller.response;

import com.surimap.account.service.SecurityContextSnapshot;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import java.util.List;

public class SecurityContextResponse {

  private final String accountId;
  private final AccountType accountType;
  private final OrganizationType organizationType;
  private final Channel channel;
  private final String policePhoneId;
  private final List<String> authorities;

  private SecurityContextResponse(
      String accountId,
      AccountType accountType,
      OrganizationType organizationType,
      Channel channel,
      String policePhoneId,
      List<String> authorities) {
    this.accountId = accountId;
    this.accountType = accountType;
    this.organizationType = organizationType;
    this.channel = channel;
    this.policePhoneId = policePhoneId;
    this.authorities = List.copyOf(authorities);
  }

  public static SecurityContextResponse from(SecurityContextSnapshot snapshot) {
    return new SecurityContextResponse(
        snapshot.accountId(),
        snapshot.accountType(),
        snapshot.organizationType(),
        snapshot.channel(),
        snapshot.policePhoneId(),
        snapshot.authorities());
  }

  public String getAccountId() {
    return accountId;
  }

  public AccountType getAccountType() {
    return accountType;
  }

  public OrganizationType getOrganizationType() {
    return organizationType;
  }

  public Channel getChannel() {
    return channel;
  }

  public String getPolicePhoneId() {
    return policePhoneId;
  }

  public List<String> getAuthorities() {
    return authorities;
  }
}
