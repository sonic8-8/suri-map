package com.surimap.domain.connectionstatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountConnectionStatusService implements ConnectionStatusQuery {

  private final AccountConnectionStatusMapper mapper;
  private final Clock clock;

  public AccountConnectionStatusService(AccountConnectionStatusMapper mapper, Clock clock) {
    this.mapper = mapper;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConnectionStatusRow> byIncident(UUID incidentId) {
    Instant now = clock.instant();
    return mapper.findFieldAccountsByIncident(incidentId).stream()
        .map(row -> row.withStatusAt(now))
        .toList();
  }
}
