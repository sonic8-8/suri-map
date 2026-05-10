package com.surimap.sync.outbox;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutboxRequeueErrorResponse(
    String error,
    String outboxStatus,
    Boolean retryable,
    String diagnosticState,
    String userSafeFailureCategory) {}
