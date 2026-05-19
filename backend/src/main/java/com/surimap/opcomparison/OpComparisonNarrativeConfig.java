package com.surimap.opcomparison;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpComparisonNarrativeConfig {

  @Bean
  @ConditionalOnMissingBean
  OpComparisonNarrativeValidator opComparisonNarrativeValidator(ObjectMapper objectMapper) {
    return new OpComparisonNarrativeValidator(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(OpComparisonNarrativePort.class)
  OpComparisonNarrativePort unavailableOpComparisonNarrativePort() {
    return request -> OpComparisonNarrativeResult.failed();
  }
}
