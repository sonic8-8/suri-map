package com.surimap.domain.path;

import java.time.Instant;
import java.util.UUID;

public record SearchPath(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID policePhoneId,
    UUID accountId,
    SearchPathStatus status,
    long version,
    Instant startedAt,
    Instant endedAt) {}
