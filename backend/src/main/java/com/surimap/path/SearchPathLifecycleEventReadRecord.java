package com.surimap.path;

import java.time.Instant;
import java.util.UUID;

public record SearchPathLifecycleEventReadRecord(
    UUID id,
    UUID searchPathId,
    String eventType,
    Instant clientTs,
    Instant serverReceivedAt,
    UUID actorPolicePhoneId,
    long version) {}
