package com.surimap.client.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenAiComparisonPropertiesTest {

  @Test
  void normalizesBaseUrlAndDefaultsModelAndTimeout() {
    OpenAiComparisonProperties properties =
        new OpenAiComparisonProperties(true, "https://api.openai.com/v1/", "key", "", 0);

    assertThat(properties.model()).isEqualTo("gpt-5-mini");
    assertThat(properties.timeoutMs()).isEqualTo(15000);
    assertThat(properties.responsesUri()).hasToString("https://api.openai.com/v1/responses");
    assertThat(properties.configured()).isTrue();
  }

  @Test
  void configuredRequiresEnabledAndApiKey() {
    assertThat(
            new OpenAiComparisonProperties(
                    false, "https://api.openai.com/v1", "key", "gpt-5-mini", 1000)
                .configured())
        .isFalse();
    assertThat(
            new OpenAiComparisonProperties(
                    true, "https://api.openai.com/v1", "", "gpt-5-mini", 1000)
                .configured())
        .isFalse();
  }
}
