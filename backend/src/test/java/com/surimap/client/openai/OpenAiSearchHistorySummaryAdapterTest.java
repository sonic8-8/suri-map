package com.surimap.client.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.domain.summary.ForbiddenSummaryGuard;
import com.surimap.domain.summary.SearchHistorySummaryPort.GenerationStatus;
import com.surimap.domain.summary.SearchHistorySummaryPort.SummaryRequest;
import com.surimap.domain.summary.SearchHistorySummaryPort.SummaryResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class OpenAiSearchHistorySummaryAdapterTest {

  private static final UUID SUMMARY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID INCIDENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void generatePostsSanitizedEvidenceWithStrictJsonSchema() throws Exception {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    OpenAiSearchHistorySummaryAdapter adapter =
        newAdapter(restTemplate, newProperties(true, "test-openai-key"));

    String summaryText =
        "OP2 동안 수색 경로 2건이 기록되었습니다. 단서 마커 1건이 남았습니다. 인수인계 메모 1건이 포함되었습니다. source readiness는 READY입니다.";
    String responseBody =
        objectMapper.writeValueAsString(
            Map.of(
                "output",
                List.of(
                    Map.of(
                        "type",
                        "message",
                        "content",
                        List.of(
                            Map.of(
                                "type",
                                "output_text",
                                "text",
                                objectMapper.writeValueAsString(
                                    Map.of("summary", summaryText))))))));

    server
        .expect(requestTo("https://api.openai.com/v1/responses"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer test-openai-key"))
        .andExpect(jsonPath("$.model").value("gpt-5-mini"))
        .andExpect(jsonPath("$.store").value(false))
        .andExpect(jsonPath("$.input[0].role").value("system"))
        .andExpect(jsonPath("$.input[0].content", containsString("입력 evidence에 있는 사실만")))
        .andExpect(jsonPath("$.input[1].role").value("user"))
        .andExpect(jsonPath("$.input[1].content", containsString(SUMMARY_ID.toString())))
        .andExpect(jsonPath("$.input[1].content", containsString("markerCount")))
        .andExpect(content().string(not(containsString("37.12345"))))
        .andExpect(content().string(not(containsString("127.56789"))))
        .andExpect(content().string(not(containsString("account-001"))))
        .andExpect(content().string(not(containsString("phone-001"))))
        .andExpect(content().string(not(containsString("https://photo.example/a.jpg"))))
        .andExpect(jsonPath("$.text.format.type").value("json_schema"))
        .andExpect(jsonPath("$.text.format.name").value("search_history_summary"))
        .andExpect(jsonPath("$.text.format.strict").value(true))
        .andExpect(jsonPath("$.text.format.schema.required[0]").value("summary"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    SummaryResult result =
        adapter.generate(new SummaryRequest(SUMMARY_ID, OP_ID, INCIDENT_ID, evidence()));

    assertThat(result.status()).isEqualTo(GenerationStatus.READY);
    assertThat(result.summaryText()).isEqualTo(summaryText);
    server.verify();
  }

  @Test
  void generateReturnsFailedWithoutHttpWhenProviderIsNotConfigured() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    OpenAiSearchHistorySummaryAdapter adapter = newAdapter(restTemplate, newProperties(false, ""));

    SummaryResult result =
        adapter.generate(new SummaryRequest(SUMMARY_ID, OP_ID, INCIDENT_ID, evidence()));

    assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
    assertThat(result.summaryText()).isNull();
    server.verify();
  }

  @Test
  void generateReturnsFailedWhenProviderReturnsNonSummaryJson() throws Exception {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    OpenAiSearchHistorySummaryAdapter adapter =
        newAdapter(restTemplate, newProperties(true, "test-openai-key"));
    String responseBody =
        objectMapper.writeValueAsString(
            Map.of("output_text", objectMapper.writeValueAsString(Map.of("items", List.of()))));

    server
        .expect(requestTo("https://api.openai.com/v1/responses"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    SummaryResult result =
        adapter.generate(new SummaryRequest(SUMMARY_ID, OP_ID, INCIDENT_ID, evidence()));

    assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
    assertThat(result.summaryText()).isNull();
    server.verify();
  }

  @Test
  void generateReturnsFailedWhenSummaryContainsForbiddenPhrase() throws Exception {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    OpenAiSearchHistorySummaryAdapter adapter =
        newAdapter(restTemplate, newProperties(true, "test-openai-key"));
    String responseBody =
        objectMapper.writeValueAsString(
            Map.of(
                "output_text",
                objectMapper.writeValueAsString(Map.of("summary", "다음 구역 추천: 북쪽으로 이동. 위험도 높음."))));

    server
        .expect(requestTo("https://api.openai.com/v1/responses"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    SummaryResult result =
        adapter.generate(new SummaryRequest(SUMMARY_ID, OP_ID, INCIDENT_ID, evidence()));

    assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
    assertThat(result.summaryText()).isNull();
    server.verify();
  }

  @Test
  void generateReturnsFailedWhenSummarySentenceCountIsOutsideRange() throws Exception {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    OpenAiSearchHistorySummaryAdapter adapter =
        newAdapter(restTemplate, newProperties(true, "test-openai-key"));
    String responseBody =
        objectMapper.writeValueAsString(
            Map.of(
                "output_text",
                objectMapper.writeValueAsString(
                    Map.of("summary", "OP2 동안 수색 경로 2건과 단서 마커 1건이 기록되었습니다."))));

    server
        .expect(requestTo("https://api.openai.com/v1/responses"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

    SummaryResult result =
        adapter.generate(new SummaryRequest(SUMMARY_ID, OP_ID, INCIDENT_ID, evidence()));

    assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
    assertThat(result.summaryText()).isNull();
    server.verify();
  }

  private OpenAiSearchHistorySummaryAdapter newAdapter(
      RestTemplate restTemplate, OpenAiComparisonProperties properties) {
    return new OpenAiSearchHistorySummaryAdapter(
        properties, restTemplate, objectMapper, new ForbiddenSummaryGuard());
  }

  private OpenAiComparisonProperties newProperties(boolean enabled, String apiKey) {
    return new OpenAiComparisonProperties(
        enabled, "https://api.openai.com/v1", apiKey, "gpt-5-mini", 1000);
  }

  private String evidence() {
    return """
        {
          "markerCount": 1,
          "memoCount": 1,
          "pathCount": 2,
          "lat": 37.12345,
          "lng": 127.56789,
          "accountId": "account-001",
          "policePhoneId": "phone-001",
          "photoUrl": "https://photo.example/a.jpg",
          "timeline": [
            {
              "occurredAt": "2026-05-19T00:00:00Z",
              "type": "MARKER_CREATED",
              "label": "단서",
              "detail": {
                "memo": "배수로 입구 단서 기록",
                "latitude": 37.12345,
                "longitude": 127.56789
              }
            }
          ]
        }
        """;
  }
}
