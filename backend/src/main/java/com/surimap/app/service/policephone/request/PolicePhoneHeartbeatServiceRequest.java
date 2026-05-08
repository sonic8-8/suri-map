package com.surimap.app.service.policephone.request;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import java.time.Instant;
import java.util.UUID;

public final class PolicePhoneHeartbeatServiceRequest {

  private final UUID policePhoneId;
  private final String accountId;
  private final AccountType accountType;
  private final OrganizationType organizationType;
  private final Instant clientTs;
  private final long sequence;
  private final Instant lastSyncAt;
  private final Integer batteryPercent;

  public PolicePhoneHeartbeatServiceRequest(
      UUID policePhoneId,
      String accountId,
      AccountType accountType,
      OrganizationType organizationType,
      Instant clientTs,
      long sequence,
      Instant lastSyncAt,
      Integer batteryPercent) {
    this.policePhoneId = policePhoneId;
    this.accountId = accountId;
    this.accountType = accountType;
    this.organizationType = organizationType;
    this.clientTs = clientTs;
    this.sequence = sequence;
    this.lastSyncAt = lastSyncAt;
    this.batteryPercent = batteryPercent;
  }

  public UUID policePhoneId() {
    return policePhoneId;
  }

  public String accountId() {
    return accountId;
  }

  public AccountType accountType() {
    return accountType;
  }

  public OrganizationType organizationType() {
    return organizationType;
  }

  public Instant clientTs() {
    return clientTs;
  }

  public long sequence() {
    return sequence;
  }

  public Instant lastSyncAt() {
    return lastSyncAt;
  }

  public Integer batteryPercent() {
    return batteryPercent;
  }
}
