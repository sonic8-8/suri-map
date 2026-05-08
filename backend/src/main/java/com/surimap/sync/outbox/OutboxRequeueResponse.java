package com.surimap.sync.outbox;

public record OutboxRequeueResponse(
    String operationId,
    boolean accepted,
    String serverTs,
    String outboxStatus,
    boolean retryable,
    String diagnosticState,
    String userSafeFailureCategory) {}
