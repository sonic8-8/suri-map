package com.surimap.sync.idempotency;

import java.util.Optional;

public interface IdempotencyRecordRepository {

  Optional<IdempotencyRecord> find(String idempotencyKey);

  void save(IdempotencyRecord record);

  void delete(String idempotencyKey);
}
