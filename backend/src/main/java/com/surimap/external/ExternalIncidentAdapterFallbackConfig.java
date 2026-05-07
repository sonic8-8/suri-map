package com.surimap.external;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExternalIncidentAdapterFallbackConfig {

  // mock 112나 실제 112 adapter bean이 들어오면 이 bean은 등록되지 않는다.
  @Bean
  @ConditionalOnMissingBean(ExternalIncidentAdapter.class)
  ExternalIncidentAdapter unavailableExternalIncidentAdapter() {
    return new UnavailableExternalIncidentAdapter();
  }
}
