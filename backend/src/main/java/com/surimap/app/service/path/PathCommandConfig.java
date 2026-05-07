package com.surimap.app.service.path;

import com.surimap.domain.path.SearchPathPublishRequest;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.domain.path.port.PolicePhoneGuard;
import com.surimap.domain.path.port.SearchPathEventPublisher;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

@Configuration
@Order(Integer.MAX_VALUE)
public class PathCommandConfig {

  private static final String EVENT_SCHEMA_VERSION = "1";
  private final Map<UUID, OperationalPeriodRow> inMemoryCurrentOps = new ConcurrentHashMap<>();

  @Bean
  AppSearchPathCommandService appSearchPathCommandService(
      OperationalPeriodQuery operationalPeriodQuery,
      PolicePhoneGuard policePhoneGuard,
      SearchPathEventPublisher searchPathEventPublisher) {
    return new AppSearchPathCommandService(
        operationalPeriodQuery, policePhoneGuard, searchPathEventPublisher);
  }

  @Bean
  @Primary
  OperationalPeriodQuery operationalPeriodQuery() {
    return new OperationalPeriodQuery() {
      @Override
      public Optional<OperationalPeriodRow> current(UUID incidentId) {
        OperationalPeriodRow row =
            inMemoryCurrentOps.computeIfAbsent(
                incidentId,
                key ->
                    new OperationalPeriodRow(
                        stableUuid(key, "current-op"),
                        key,
                        "ACTIVE",
                        1,
                        Instant.now(),
                        null,
                        null,
                        1L));
        return Optional.of(row);
      }

      @Override
      public List<OperationalPeriodRow> list(UUID incidentId) {
        return current(incidentId).map(List::of).orElse(List.of());
      }
    };
  }

  @Bean
  @Primary
  PolicePhoneGuard policePhoneGuard() {
    return (policePhoneId, opId) -> {
      if (policePhoneId == null) {
        throw new SearchPathGuardException("police_phone_not_registered");
      }
      if (opId == null) {
        throw new SearchPathGuardException("police_phone_not_assigned");
      }
    };
  }

  @Bean
  @Primary
  SearchPathEventPublisher searchPathEventPublisher() {
    return new SearchPathEventPublisher() {
      @Override
      public void publish(SearchPathPublishRequest request) {
        if (request == null) {
          throw new SearchPathGuardException("write_conflict");
        }
        if (request.eventType() == null || request.id() == null || request.opId() == null) {
          throw new SearchPathGuardException("write_conflict");
        }
        if (EVENT_SCHEMA_VERSION.isBlank()) {
          throw new SearchPathGuardException("write_conflict");
        }
      }
    };
  }

  private static UUID stableUuid(UUID key, String namespace) {
    return UUID.nameUUIDFromBytes((namespace + ":" + key).getBytes());
  }
}
