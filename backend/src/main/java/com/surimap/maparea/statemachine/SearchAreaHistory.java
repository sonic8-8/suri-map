package com.surimap.maparea.statemachine;

import java.time.Instant;
import java.util.UUID;

public record SearchAreaHistory(
    UUID id,
    UUID searchAreaId,
    UUID opId,
    String changeType,
    String previousStatus,
    String nextStatus,
    String changeMemo,
    UUID changedByAccountId,
    Instant changedAt
) {}
