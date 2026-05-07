package com.surimap.sync.clock;

public record SyncClockResponse(
    String clientTs,
    String serverTs,
    long clockOffsetMs,
    String clockSyncedAt,
    long maxAllowedSkewMs) {}
