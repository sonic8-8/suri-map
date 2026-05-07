package com.surimap.marker.config;

import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.marker.domain.exception.InvalidGeometryException;
import com.surimap.marker.domain.port.MarkerLocationValidator;
import com.surimap.marker.domain.port.OperationalPeriodQueryPort;
import com.surimap.marker.domain.service.MarkerLocationValidatorImpl;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarkerCreateConfig {

  @Bean
  @ConditionalOnMissingBean(MarkerLocationValidator.class)
  MarkerLocationValidator markerLocationValidator(ObjectProvider<SearchAreaQuery> searchAreaQuery) {
    SearchAreaQuery query = searchAreaQuery.getIfAvailable();
    if (query == null) {
      return (incidentId, location) -> {
        throw new InvalidGeometryException("SearchAreaQuery unavailable");
      };
    }
    return new MarkerLocationValidatorImpl(query);
  }

  @Bean
  @ConditionalOnMissingBean(OperationalPeriodQueryPort.class)
  OperationalPeriodQueryPort markerOperationalPeriodQueryPort(
      ObjectProvider<OperationalPeriodQuery> operationalPeriodQuery) {
    return incidentId ->
        Optional.ofNullable(operationalPeriodQuery.getIfAvailable())
            .flatMap(query -> query.current(incidentId))
            .map(row -> row.opId());
  }

  @Bean
  @ConditionalOnMissingBean(MarkerOpBindingValidator.class)
  MarkerOpBindingValidator markerOpBindingValidator(
      OperationalPeriodQueryPort operationalPeriodQueryPort) {
    return new MarkerOpBindingValidator(operationalPeriodQueryPort);
  }
}
