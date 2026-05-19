package com.surimap.client.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.opcomparison.OpComparisonNarrativePort;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(OpenAiComparisonProperties.class)
class OpenAiComparisonConfig {

  @Bean("openAiRestTemplate")
  @ConditionalOnMissingBean(name = "openAiRestTemplate")
  RestTemplate openAiRestTemplate(OpenAiComparisonProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    Duration timeout = Duration.ofMillis(properties.timeoutMs());
    requestFactory.setConnectTimeout(timeout);
    requestFactory.setReadTimeout(timeout);
    return new RestTemplate(requestFactory);
  }

  @Bean
  @ConditionalOnProperty(prefix = "surimap.ai.openai", name = "enabled", havingValue = "true")
  OpComparisonNarrativePort openAiComparisonAdapter(
      OpenAiComparisonProperties properties,
      @Qualifier("openAiRestTemplate") RestTemplate restTemplate,
      ObjectMapper objectMapper) {
    return new OpenAiComparisonAdapter(properties, restTemplate, objectMapper);
  }
}
