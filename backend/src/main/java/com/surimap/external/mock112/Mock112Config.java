package com.surimap.external.mock112;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * mock 112 연동 설정.
 * mock112.enabled=true 일 때만 활성화된다.
 */
@Configuration
@ConditionalOnProperty(name = "mock112.enabled", havingValue = "true")
public class Mock112Config {

    @Bean
    @ConfigurationProperties(prefix = "mock112")
    public Mock112Properties mock112Properties() {
        return new Mock112Properties();
    }

    @Bean("mock112RestTemplate")
    public RestTemplate mock112RestTemplate() {
        return new RestTemplate();
    }

    public static class Mock112Properties {
        private String baseUrl = "http://localhost:18112";
        private PollingProperties polling = new PollingProperties();

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public PollingProperties getPolling() { return polling; }
        public void setPolling(PollingProperties polling) { this.polling = polling; }

        public static class PollingProperties {
            private long intervalMs = 5000;

            public long getIntervalMs() { return intervalMs; }
            public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        }
    }
}
