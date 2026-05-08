package com.surimap.policephone;

import java.time.Instant;
import java.util.UUID;

public record PolicePhoneHeartbeatResult(
    UUID id,
    PolicePhoneFreshnessStatus status,
    long version,
    UUID incidentId,
    UUID policePhoneId,
    long sequence,
    Instant lastHeartbeatAt,
    Instant lastSyncAt,
    boolean accepted) {}
