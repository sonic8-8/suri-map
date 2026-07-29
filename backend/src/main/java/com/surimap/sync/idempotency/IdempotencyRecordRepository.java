package com.surimap.sync.idempotency;

import java.util.Optional;

public interface IdempotencyRecordRepository {

  Optional<IdempotencyRecord> find(String endpoint, String idempotencyKey);

  boolean reserve(IdempotencyRecord record);

  void save(IdempotencyRecord record);

  void delete(String endpoint, String idempotencyKey);
}
