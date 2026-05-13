package com.surimap.sync.idempotency;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIdempotencyRecordRepository implements IdempotencyRecordRepository {

  private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

  @Override
  public Optional<IdempotencyRecord> find(String idempotencyKey) {
    return Optional.ofNullable(records.get(idempotencyKey));
  }

  @Override
  public void save(IdempotencyRecord record) {
    records.put(record.idempotencyKey(), record);
  }

  @Override
  public void delete(String idempotencyKey) {
    records.remove(idempotencyKey);
  }
}
