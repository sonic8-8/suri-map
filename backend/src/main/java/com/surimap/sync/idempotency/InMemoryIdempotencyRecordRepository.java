package com.surimap.sync.idempotency;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIdempotencyRecordRepository implements IdempotencyRecordRepository {

  private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

  @Override
  public Optional<IdempotencyRecord> find(String endpoint, String idempotencyKey) {
    return Optional.ofNullable(records.get(identity(endpoint, idempotencyKey)));
  }

  @Override
  public boolean reserve(IdempotencyRecord record) {
    return records.putIfAbsent(identity(record.endpoint(), record.idempotencyKey()), record)
        == null;
  }

  @Override
  public void save(IdempotencyRecord record) {
    records.put(identity(record.endpoint(), record.idempotencyKey()), record);
  }

  @Override
  public void delete(String endpoint, String idempotencyKey) {
    records.remove(identity(endpoint, idempotencyKey));
  }

  private static String identity(String endpoint, String idempotencyKey) {
    return endpoint + '\0' + idempotencyKey;
  }
}
