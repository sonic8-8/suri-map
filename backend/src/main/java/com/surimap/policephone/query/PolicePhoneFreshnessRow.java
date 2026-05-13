package com.surimap.policephone.query;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import com.surimap.policephone.PolicePhoneFreshnessStatus;
import java.time.Instant;
import java.util.UUID;

public record PolicePhoneFreshnessRow(
    UUID policePhoneId,
    String accountId,
    AccountType accountType,
    OrganizationType organizationType,
    UUID incidentId,
    UUID opId,
    UUID latestEventId,
    Instant lastHeartbeatAt,
    Instant lastSyncAt,
    long version,
    long heartbeatSequence,
    PolicePhoneFreshnessStatus derivedFreshness) {}
