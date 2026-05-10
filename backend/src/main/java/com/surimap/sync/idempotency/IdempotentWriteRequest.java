package com.surimap.sync.idempotency;

public record IdempotentWriteRequest(
    String endpoint, String operationId, String entityId, String idempotencyKey, String bodyHash) {}
