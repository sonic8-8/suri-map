package com.surimap.incident.adapter;

import com.surimap.marker.domain.port.ReferenceMarkerSeed;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReferenceMarkerSeedFallbackConfig {

  // S5 production bean이 들어오면 이 bean은 등록되지 않는다.
  @Bean
  @ConditionalOnMissingBean(ReferenceMarkerSeed.class)
  ReferenceMarkerSeed blockingReferenceMarkerSeed() {
    return new BlockingReferenceMarkerSeed();
  }
}
