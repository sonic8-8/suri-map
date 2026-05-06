package com.surimap.common.auth;

import java.util.Collection;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class SuriMapAuthentication extends AbstractAuthenticationToken {

  private final UUID accountId;
  private final AccountType accountType;
  private final Channel channel;
  private final UUID policePhoneId;

  public SuriMapAuthentication(
      UUID accountId,
      AccountType accountType,
      Channel channel,
      UUID policePhoneId,
      Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.accountId = accountId;
    this.accountType = accountType;
    this.channel = channel;
    this.policePhoneId = policePhoneId;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public Object getPrincipal() {
    return accountId.toString();
  }

  public UUID getAccountId() {
    return accountId;
  }

  public AccountType getAccountType() {
    return accountType;
  }

  public Channel getChannel() {
    return channel;
  }

  public UUID getPolicePhoneId() {
    return policePhoneId;
  }
}
