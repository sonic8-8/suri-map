package com.surimap.summary;

import com.surimap.domain.summary.ForbiddenSummaryGuard;
import com.surimap.domain.summary.SearchHistorySummaryPort;
import com.surimap.domain.summary.SearchHistorySummaryPort.SummaryResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SearchHistorySummaryConfig {

  @Bean
  @ConditionalOnMissingBean
  ForbiddenSummaryGuard forbiddenSummaryGuard() {
    return new ForbiddenSummaryGuard();
  }

  @Bean
  @ConditionalOnMissingBean(SearchHistorySummaryPort.class)
  SearchHistorySummaryPort unavailableSearchHistorySummaryPort() {
    return request -> SummaryResult.failed();
  }
}
