package com.surimap.policephone;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import java.time.Instant;
import java.util.UUID;

public record PolicePhoneFreshnessStateRow(
    UUID policePhoneId,
    UUID accountId,
    AccountType accountType,
    OrganizationType organizationType,
    UUID incidentId,
    UUID lastHeartbeatEventId,
    Instant lastHeartbeatAt,
    Instant lastSyncAt,
    long version,
    long heartbeatSequence) {

  public PolicePhoneFreshnessStateRow(
      UUID policePhoneId,
      UUID accountId,
      AccountType accountType,
      OrganizationType organizationType,
      UUID incidentId,
      UUID lastHeartbeatEventId,
      Instant lastHeartbeatAt,
      Instant lastSyncAt,
      Long version,
      Long heartbeatSequence) {
    this(
        policePhoneId,
        accountId,
        accountType,
        organizationType,
        incidentId,
        lastHeartbeatEventId,
        lastHeartbeatAt,
        lastSyncAt,
        version == null ? 0L : version,
        heartbeatSequence == null ? 0L : heartbeatSequence);
  }
}
