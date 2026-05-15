package com.surimap.config;

import com.surimap.maparea.geometry.policy.GeometryPolicy;
import com.surimap.maparea.geometry.validation.GeometryValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers geometry validation beans. */
@Configuration
public class GeometryConfig {

  /** Registers the S2 harness default GeometryPolicy bean. */
  @Bean
  public GeometryPolicy geometryPolicy() {
    return GeometryPolicy.s2HarnessDefault();
  }

  /** Registers the GeometryValidator bean backed by the S2 policy. */
  @Bean
  public GeometryValidator geometryValidator(GeometryPolicy geometryPolicy) {
    return new GeometryValidator(geometryPolicy);
  }
}
