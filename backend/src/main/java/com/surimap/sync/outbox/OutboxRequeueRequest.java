package com.surimap.sync.outbox;

public record OutboxRequeueRequest(
    String operationId,
    String incidentId,
    String reason,
    String clientTs,
    Long clockOffsetMs,
    String clockSyncedAt,
    Integer attemptCount) {}
