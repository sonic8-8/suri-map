package com.surimap.common.auth;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class SuriMapAuthentication extends AbstractAuthenticationToken {

  private final UUID accountId;
  private final AccountType accountType;
  private final OrganizationType organizationType;
  private final Channel channel;
  private final UUID policePhoneId;

  public SuriMapAuthentication(
      UUID accountId,
      AccountType accountType,
      OrganizationType organizationType,
      Channel channel,
      UUID policePhoneId,
      Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
    this.accountType = Objects.requireNonNull(accountType, "accountType must not be null");
    this.organizationType =
        Objects.requireNonNull(organizationType, "organizationType must not be null");
    this.channel = Objects.requireNonNull(channel, "channel must not be null");
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

  public OrganizationType getOrganizationType() {
    return organizationType;
  }

  public Channel getChannel() {
    return channel;
  }

  public UUID getPolicePhoneId() {
    return policePhoneId;
  }
}
