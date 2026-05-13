package com.surimap.offlinepackage.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TileserverConfig {

  @Bean
  @ConfigurationProperties(prefix = "tileserver")
  public TileserverProperties tileserverProperties() {
    return new TileserverProperties();
  }

  @Bean("tileserverRestTemplate")
  public RestTemplate tileserverRestTemplate() {
    return new RestTemplate();
  }
}
