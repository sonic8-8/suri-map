package com.surimap.sync.idempotency;

import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotentWriteService {

  private static final String APPLICATION_JSON = "application/json";
  private static final int DEFAULT_SCHEMA_VERSION = 1;

  private final OwnerReplayRecoveryPort recoveryPort;
  private final IdempotencyRecordRepository repository;

  public IdempotentWriteService() {
    this(
        (endpoint, operationId, entityId) -> java.util.Optional.empty(),
        new InMemoryIdempotencyRecordRepository());
  }

  public IdempotentWriteService(OwnerReplayRecoveryPort recoveryPort) {
    this(recoveryPort, new InMemoryIdempotencyRecordRepository());
  }

  @Autowired
  public IdempotentWriteService(
      OwnerReplayRecoveryPort recoveryPort, IdempotencyRecordRepository repository) {
    this.recoveryPort = Objects.requireNonNull(recoveryPort, "recoveryPort must not be null");
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
  }

  @Transactional
  public IdempotentWriteResponse reserveAndReplay(
      IdempotentWriteRequest request, Supplier<IdempotentWriteResponse> ownerOperation) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(ownerOperation, "ownerOperation must not be null");

    var existing = repository.find(request.endpoint(), request.idempotencyKey());
    if (existing.isEmpty() && repository.reserve(IdempotencyRecord.reserved(request))) {
      try {
        IdempotentWriteResponse response = ownerOperation.get();
        repository.save(IdempotencyRecord.committed(request, response.statusCode(), response));
        return response;
      } catch (RuntimeException | Error exception) {
        repository.delete(request.endpoint(), request.idempotencyKey());
        throw exception;
      }
    }

    if (existing.isEmpty()) {
      existing = repository.find(request.endpoint(), request.idempotencyKey());
    }
    if (existing.isEmpty()) {
      return conflict("write_conflict");
    }

    IdempotencyRecord record = existing.get();
    if (!record.bodyHash().equals(request.bodyHash())) {
      return conflict("idempotency_mismatch");
    }

    if (record.status() != IdempotencyRecordStatus.COMMITTED) {
      return conflict("write_conflict");
    }

    if (record.response() != null) {
      return record.response().asReplay(record.replayRecovered());
    }

    return recoverCommittedResponse(record);
  }

  public synchronized void recordCommittedWithoutResponse(
      IdempotentWriteRequest request, int statusCode, long entityVersion, long entitySequence) {
    Objects.requireNonNull(request, "request must not be null");
    repository.save(
        IdempotencyRecord.committedWithoutResponse(
            request, statusCode, entityVersion, entitySequence));
  }

  private IdempotentWriteResponse recoverCommittedResponse(IdempotencyRecord record) {
    var recovered =
        recoveryPort.recover(record.endpoint(), record.operationId(), record.entityId());

    if (recovered.isEmpty() || !matchesCommittedEntity(record, recovered.get())) {
      return conflict("write_conflict");
    }

    IdempotentWriteResponse replay = recovered.get().asReplay(true);
    repository.save(record.withResponse(replay));
    return replay;
  }

  private boolean matchesCommittedEntity(
      IdempotencyRecord record, IdempotentWriteResponse recoveredResponse) {
    return record.entityVersion() == recoveredResponse.entityVersion()
        && record.entitySequence() == recoveredResponse.entitySequence();
  }

  private static IdempotentWriteResponse conflict(String error) {
    return new IdempotentWriteResponse(
        409,
        "{\"error\":\"" + error + "\"}",
        APPLICATION_JSON,
        DEFAULT_SCHEMA_VERSION,
        null,
        null,
        0L,
        0L,
        false,
        false,
        error);
  }
}
