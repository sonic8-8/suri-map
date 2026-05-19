package com.surimap.client.openai;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "surimap.ai.openai")
public record OpenAiComparisonProperties(
    boolean enabled, String baseUrl, String apiKey, String model, long timeoutMs) {

  private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
  private static final String DEFAULT_MODEL = "gpt-5-mini";
  private static final long DEFAULT_TIMEOUT_MS = 15000;

  public OpenAiComparisonProperties {
    baseUrl = normalizeBaseUrl(baseUrl);
    apiKey = apiKey == null ? "" : apiKey.trim();
    model = StringUtils.hasText(model) ? model.trim() : DEFAULT_MODEL;
    timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
  }

  boolean configured() {
    return enabled && StringUtils.hasText(apiKey) && StringUtils.hasText(model);
  }

  URI responsesUri() {
    return URI.create(baseUrl + "/responses");
  }

  private static String normalizeBaseUrl(String value) {
    String normalized = StringUtils.hasText(value) ? value.trim() : DEFAULT_BASE_URL;
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
