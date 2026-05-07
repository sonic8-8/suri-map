package com.surimap.sync.outbox;

public record OutboxRequeueResponse(String operationId, boolean accepted, String serverTs) {}
