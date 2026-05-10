package com.surimap.sync.idempotency;

public record IdempotentWriteResponse(
    int statusCode,
    String bodyJson,
    String contentType,
    int responseSchemaVersion,
    String entityId,
    String entityStatus,
    long entityVersion,
    long entitySequence,
    boolean replayed,
    boolean replayRecovered,
    String error) {

  public IdempotentWriteResponse(
      int statusCode,
      String bodyJson,
      String contentType,
      int responseSchemaVersion,
      long entityVersion,
      long entitySequence,
      boolean replayed,
      boolean replayRecovered,
      String error) {
    this(
        statusCode,
        bodyJson,
        contentType,
        responseSchemaVersion,
        null,
        null,
        entityVersion,
        entitySequence,
        replayed,
        replayRecovered,
        error);
  }

  IdempotentWriteResponse asReplay(boolean recovered) {
    return new IdempotentWriteResponse(
        statusCode,
        bodyJson,
        contentType,
        responseSchemaVersion,
        entityId,
        entityStatus,
        entityVersion,
        entitySequence,
        true,
        recovered,
        error);
  }
}
