package com.surimap.external.mock112;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/** mock 112 연동 설정. mock112.enabled=true 일 때만 활성화된다. */
@Configuration
@ConditionalOnProperty(name = "mock112.enabled", havingValue = "true")
public class Mock112Config {

  @Bean
  @ConfigurationProperties(prefix = "mock112")
  public Mock112Properties mock112Properties() {
    return new Mock112Properties();
  }

  @Bean("mock112RestTemplate")
  public RestTemplate mock112RestTemplate(Mock112Properties properties) {
    RestTemplate restTemplate = new RestTemplate();
    if (StringUtils.hasText(properties.getInternalApi().getToken())) {
      restTemplate
          .getInterceptors()
          .add(
              (request, body, execution) -> {
                request
                    .getHeaders()
                    .set("X-Internal-Service-Token", properties.getInternalApi().getToken());
                return execution.execute(request, body);
              });
    }
    return restTemplate;
  }

  public static class Mock112Properties {
    private String baseUrl = "http://localhost:18112";
    private PollingProperties polling = new PollingProperties();
    private InternalApiProperties internalApi = new InternalApiProperties();

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public PollingProperties getPolling() {
      return polling;
    }

    public void setPolling(PollingProperties polling) {
      this.polling = polling;
    }

    public InternalApiProperties getInternalApi() {
      return internalApi;
    }

    public void setInternalApi(InternalApiProperties internalApi) {
      this.internalApi = internalApi;
    }

    public static class PollingProperties {
      private long intervalMs = 5000;

      public long getIntervalMs() {
        return intervalMs;
      }

      public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
      }
    }

    public static class InternalApiProperties {
      private String token = "";

      public String getToken() {
        return token;
      }

      public void setToken(String token) {
        this.token = token;
      }
    }
  }
}
