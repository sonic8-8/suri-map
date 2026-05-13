package com.surimap.policephone;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import java.time.Instant;
import java.util.UUID;

public record PolicePhoneStateRow(
    UUID id,
    UUID accountId,
    AccountType accountType,
    OrganizationType organizationType,
    boolean registered,
    String status,
    UUID lastHeartbeatEventId,
    Instant lastHeartbeatAt,
    Instant lastSyncAt,
    long heartbeatSequence,
    long version) {

  public PolicePhoneStateRow(
      UUID id,
      UUID accountId,
      AccountType accountType,
      OrganizationType organizationType,
      Boolean registered,
      String status,
      UUID lastHeartbeatEventId,
      Instant lastHeartbeatAt,
      Instant lastSyncAt,
      Long heartbeatSequence,
      Long version) {
    this(
        id,
        accountId,
        accountType,
        organizationType,
        registered != null && registered,
        status,
        lastHeartbeatEventId,
        lastHeartbeatAt,
        lastSyncAt,
        heartbeatSequence == null ? 0L : heartbeatSequence,
        version == null ? 0L : version);
  }
}
