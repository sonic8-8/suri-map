package com.surimap.opcomparison;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpComparisonNarrativeConfig {

  @Bean
  @ConditionalOnMissingBean(OpComparisonNarrativePort.class)
  OpComparisonNarrativePort unavailableOpComparisonNarrativePort() {
    return request -> OpComparisonNarrativeResult.failed();
  }
}
