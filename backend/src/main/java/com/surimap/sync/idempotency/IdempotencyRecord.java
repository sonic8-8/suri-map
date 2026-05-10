package com.surimap.sync.idempotency;

import java.util.Objects;

public record IdempotencyRecord(
    String endpoint,
    String operationId,
    String entityId,
    String idempotencyKey,
    String bodyHash,
    IdempotencyRecordStatus status,
    int responseStatus,
    long entityVersion,
    long entitySequence,
    IdempotentWriteResponse response,
    boolean replayRecovered) {

  public IdempotencyRecord {
    Objects.requireNonNull(endpoint, "endpoint must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    Objects.requireNonNull(bodyHash, "bodyHash must not be null");
    Objects.requireNonNull(status, "status must not be null");
  }

  static IdempotencyRecord committed(
      IdempotentWriteRequest request, int statusCode, IdempotentWriteResponse response) {
    return new IdempotencyRecord(
        request.endpoint(),
        request.operationId(),
        request.entityId(),
        request.idempotencyKey(),
        request.bodyHash(),
        IdempotencyRecordStatus.COMMITTED,
        statusCode,
        response.entityVersion(),
        response.entitySequence(),
        response,
        response.replayRecovered());
  }

  static IdempotencyRecord reserved(IdempotentWriteRequest request) {
    return new IdempotencyRecord(
        request.endpoint(),
        request.operationId(),
        request.entityId(),
        request.idempotencyKey(),
        request.bodyHash(),
        IdempotencyRecordStatus.RESERVED,
        0,
        0L,
        0L,
        null,
        false);
  }

  static IdempotencyRecord committedWithoutResponse(
      IdempotentWriteRequest request, int statusCode, long entityVersion, long entitySequence) {
    return new IdempotencyRecord(
        request.endpoint(),
        request.operationId(),
        request.entityId(),
        request.idempotencyKey(),
        request.bodyHash(),
        IdempotencyRecordStatus.COMMITTED,
        statusCode,
        entityVersion,
        entitySequence,
        null,
        false);
  }

  IdempotencyRecord withResponse(IdempotentWriteResponse recoveredResponse) {
    return new IdempotencyRecord(
        endpoint,
        operationId,
        entityId,
        idempotencyKey,
        bodyHash,
        IdempotencyRecordStatus.COMMITTED,
        recoveredResponse.statusCode(),
        recoveredResponse.entityVersion(),
        recoveredResponse.entitySequence(),
        recoveredResponse,
        recoveredResponse.replayRecovered());
  }
}
