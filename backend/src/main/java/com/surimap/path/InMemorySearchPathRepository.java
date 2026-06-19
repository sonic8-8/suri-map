package com.surimap.path;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemorySearchPathRepository implements SearchPathRepository {

  private final ConcurrentHashMap<UUID, SearchPathAggregate> storage = new ConcurrentHashMap<>();

  @Override
  public Optional<SearchPathAggregate> findById(UUID pathId) {
    return Optional.ofNullable(storage.get(pathId));
  }

  @Override
  public SearchPathAggregate save(SearchPathAggregate aggregate) {
    storage.put(aggregate.id(), aggregate);
    return aggregate;
  }

  @Override
  public List<SearchPathAggregate> findAll() {
    return new ArrayList<>(storage.values());
  }

  @Override
  public List<SearchPathAggregate> findByQuery(
      UUID incidentId, UUID opId, UUID policePhoneId, UUID accountId) {
    return storage.values().stream()
        .filter(path -> incidentId == null || incidentId.equals(path.incidentId()))
        .filter(path -> opId == null || opId.equals(path.opId()))
        .filter(path -> policePhoneId == null || policePhoneId.equals(path.policePhoneId()))
        .filter(path -> accountId == null || accountId.equals(path.accountId()))
        .toList();
  }
}
