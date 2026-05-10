package com.surimap.marker.seed;

import com.surimap.marker.domain.port.MarkerLocationValidator;
import com.surimap.marker.repository.MarkerRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** S5 ReferenceMarkerSeed service를 production bean으로 등록한다. */
@Configuration
public class ReferenceMarkerSeedConfiguration {

  @Bean
  @ConditionalOnMissingBean(ReferenceMarkerSeed.class)
  ReferenceMarkerSeed referenceMarkerSeed(
      MarkerRepository markerRepository, MarkerLocationValidator markerLocationValidator) {
    return new ReferenceMarkerSeedService(markerRepository, markerLocationValidator);
  }
}
