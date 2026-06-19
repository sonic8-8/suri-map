package com.surimap.path;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SearchPathRepository {
  Optional<SearchPathAggregate> findById(UUID pathId);

  SearchPathAggregate save(SearchPathAggregate aggregate);

  List<SearchPathAggregate> findAll();

  List<SearchPathAggregate> findByQuery(
      UUID incidentId, UUID opId, UUID policePhoneId, UUID accountId);
}
