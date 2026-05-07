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
}
