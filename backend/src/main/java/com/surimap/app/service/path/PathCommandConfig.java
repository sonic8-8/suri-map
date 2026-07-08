package com.surimap.app.service.path;

import com.surimap.domain.path.SearchPathPublishRequest;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.domain.path.port.SearchPathEventPublisher;
import com.surimap.eventhub.port.EventHub;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodQueryService;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.domain.path.SearchPathMapper;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

@Configuration
@Order(Integer.MAX_VALUE)
public class PathCommandConfig {

  private static final String EVENT_SCHEMA_VERSION = "1";
  private final Map<UUID, CurrentOpResult> inMemoryCurrentOps = new ConcurrentHashMap<>();

  @Bean
  AppSearchPathCommandService appSearchPathCommandService(
      OperationalPeriodQuery operationalPeriodQuery,
      SearchPathEventPublisher searchPathEventPublisher,
      SearchPathMapper searchPathMapper,
      Environment environment,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    return new AppSearchPathCommandService(
        operationalPeriodQuery,
        searchPathEventPublisher,
        postgresqlDataSource(environment) ? searchPathMapper : null,
        idempotentResponseCacheProvider);
  }

  @Bean
  @Primary
  OperationalPeriodQuery operationalPeriodQuery(
      ObjectProvider<OperationalPeriodQueryService> dbQueryProvider, Environment environment) {
    OperationalPeriodQuery inMemoryQuery =
        new OperationalPeriodQuery() {
          @Override
          public Optional<CurrentOpResult> current(UUID incidentId) {
            CurrentOpResult row =
                inMemoryCurrentOps.computeIfAbsent(
                    incidentId,
                    key ->
                        new CurrentOpResult(
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
            return current(incidentId)
                .map(row -> List.of(toOperationalPeriodRow(row)))
                .orElse(List.of());
          }
        };
    return new OperationalPeriodQuery() {
      @Override
      public Optional<CurrentOpResult> current(UUID incidentId) {
        OperationalPeriodQueryService dbQuery = dbQueryProvider.getIfAvailable();
        if (postgresqlDataSource(environment) && dbQuery != null) {
          return dbQuery.current(incidentId);
        }
        return inMemoryQuery.current(incidentId);
      }

      @Override
      public List<OperationalPeriodRow> list(UUID incidentId) {
        OperationalPeriodQueryService dbQuery = dbQueryProvider.getIfAvailable();
        if (postgresqlDataSource(environment) && dbQuery != null) {
          return dbQuery.list(incidentId);
        }
        return inMemoryQuery.list(incidentId);
      }
    };
  }

  @Bean
  @Primary
  SearchPathEventPublisher searchPathEventPublisher(EventHub eventHub, Environment environment) {
    if (!postgresqlDataSource(environment)) {
      return new SearchPathEventPublisher() {
        @Override
        public void publish(SearchPathPublishRequest request) {
          if (request == null) {
            throw new SearchPathGuardException("write_conflict");
          }
          if (request.eventType() == null
              || request.id() == null
              || request.incidentId() == null
              || request.opId() == null) {
            throw new SearchPathGuardException("write_conflict");
          }
          if (EVENT_SCHEMA_VERSION.isBlank()) {
            throw new SearchPathGuardException("write_conflict");
          }
        }
      };
    }
    return new EventHubSearchPathEventPublisher(eventHub);
  }

  private static UUID stableUuid(UUID key, String namespace) {
    return UUID.nameUUIDFromBytes((namespace + ":" + key).getBytes());
  }

  private static OperationalPeriodRow toOperationalPeriodRow(CurrentOpResult row) {
    return new OperationalPeriodRow(
        row.opId(),
        row.incidentId(),
        row.status(),
        row.sequenceNo(),
        row.startedAt(),
        row.endedAt(),
        row.reason(),
        row.version());
  }

  private static boolean postgresqlDataSource(Environment environment) {
    String driver = environment.getProperty("spring.datasource.driver-class-name", "");
    String url = environment.getProperty("spring.datasource.url", "");
    return driver.contains("postgresql") || url.startsWith("jdbc:postgresql:");
  }
}
