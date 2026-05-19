package com.surimap.client.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.opcomparison.OpComparisonDiffFact;
import com.surimap.opcomparison.OpComparisonDiffFactType;
import com.surimap.opcomparison.OpComparisonEvidencePackage;
import com.surimap.opcomparison.OpComparisonNarrativeRequest;
import com.surimap.opcomparison.OpComparisonNarrativeResult;
import com.surimap.opcomparison.OpComparisonNarrativeStatus;
import com.surimap.opcomparison.OpComparisonOperationalPeriodEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class OpenAiComparisonAdapterTest {

  private static final UUID COMPARISON_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID INCIDENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OP_1 = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID OP_2 = UUID.fromString("44444444-4444-4444-4444-444444444444");

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void generatePostsEvidenceWithStrictJsonSchema() throws Exception {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    OpenAiComparisonProperties properties =
        new OpenAiComparisonProperties(
            true, "https://api.openai.com/v1", "test-openai-key", "gpt-5-mini", 1000);
    OpenAiComparisonAdapter adapter =
        new OpenAiComparisonAdapter(properties, restTemplate, objectMapper);

    String observationsJson =
        """
        {"observations":[{"observation":"1차와 2차의 이동 거리 차이는 420m입니다.","evidence":[{"source":"diffFact","factId":"metric-pathDistanceMeters","operationalPeriodId":"33333333-3333-3333-3333-333333333333","key":"delta","value":"420"}]}]}
        """
            .trim();
    String responseBody =
        objectMapper.writeValueAsString(
            Map.of(
                "output",
                List.of(
                    Map.of(
                        "type",
                        "message",
                        "content",
                        List.of(Map.of("type", "output_text", "text", observationsJson))))));

    server
        .expect(requestTo("https://api.openai.com/v1/responses"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer test-openai-key"))
        .andExpect(jsonPath("$.model").value("gpt-5-mini"))
        .andExpect(jsonPath("$.store").value(false))
        .andExpect(jsonPath("$.input[0].role").value("system"))
        .andExpect(jsonPath("$.input[0].content", containsString("추론하지 않는다")))
        .andExpect(jsonPath("$.input[1].role").value("user"))
        .andExpect(jsonPath("$.input[1].content", containsString(COMPARISON_ID.toString())))
        .andExpect(jsonPath("$.input[1].content", containsString("metric-pathDistanceMeters")))
        .andExpect(jsonPath("$.text.format.type").value("json_schema"))
        .andExpect(jsonPath("$.text.format.name").value("op_comparison_observations"))
        .andExpect(jsonPath("$.text.format.strict").value(true))
        .andExpect(jsonPath("$.text.format.schema.required[0]").value("observations"))
        .andExpect(
            jsonPath("$.text.format.schema.properties.observations.items.required[0]")
                .value("observation"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    OpComparisonNarrativeResult result =
        adapter.generate(new OpComparisonNarrativeRequest(evidencePackage()));

    assertThat(result.status()).isEqualTo(OpComparisonNarrativeStatus.READY);
    assertThat(result.observationsJson()).isEqualTo(observationsJson);
    server.verify();
  }

  @Test
  void generateReturnsFailedWithoutHttpWhenProviderIsNotConfigured() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    OpenAiComparisonProperties properties =
        new OpenAiComparisonProperties(false, "https://api.openai.com/v1", "", "gpt-5-mini", 1000);
    OpenAiComparisonAdapter adapter =
        new OpenAiComparisonAdapter(properties, restTemplate, objectMapper);

    OpComparisonNarrativeResult result =
        adapter.generate(new OpComparisonNarrativeRequest(evidencePackage()));

    assertThat(result.status()).isEqualTo(OpComparisonNarrativeStatus.FAILED);
    assertThat(result.observationsJson()).isNull();
    server.verify();
  }

  @Test
  void generateReturnsFailedWhenProviderReturnsNonObservationJson() throws Exception {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    OpenAiComparisonProperties properties =
        new OpenAiComparisonProperties(
            true, "https://api.openai.com/v1", "test-openai-key", "gpt-5-mini", 1000);
    OpenAiComparisonAdapter adapter =
        new OpenAiComparisonAdapter(properties, restTemplate, objectMapper);
    String responseBody =
        objectMapper.writeValueAsString(
            Map.of(
                "output",
                List.of(
                    Map.of(
                        "type",
                        "message",
                        "content",
                        List.of(Map.of("type", "output_text", "text", "{\"items\":[]}"))))));

    server
        .expect(requestTo("https://api.openai.com/v1/responses"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    OpComparisonNarrativeResult result =
        adapter.generate(new OpComparisonNarrativeRequest(evidencePackage()));

    assertThat(result.status()).isEqualTo(OpComparisonNarrativeStatus.FAILED);
    assertThat(result.observationsJson()).isNull();
    server.verify();
  }

  private OpComparisonEvidencePackage evidencePackage() {
    return new OpComparisonEvidencePackage(
        COMPARISON_ID,
        INCIDENT_ID,
        List.of(
            new OpComparisonOperationalPeriodEvidence(
                OP_1, 1, Instant.parse("2026-05-19T00:00:00Z"), null, null),
            new OpComparisonOperationalPeriodEvidence(
                OP_2, 2, Instant.parse("2026-05-19T01:00:00Z"), null, null)),
        List.of(
            new OpComparisonDiffFact(
                "metric-pathDistanceMeters",
                OpComparisonDiffFactType.METRIC_DIFF,
                "pathDistanceMeters",
                OP_1,
                OP_2,
                BigDecimal.valueOf(1200),
                BigDecimal.valueOf(1620),
                BigDecimal.valueOf(420),
                ">=100")),
        List.of());
  }
}
