package com.surimap.client.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.opcomparison.OpComparisonNarrativePort;
import com.surimap.opcomparison.OpComparisonNarrativeRequest;
import com.surimap.opcomparison.OpComparisonNarrativeResult;
import com.surimap.opcomparison.OpComparisonNarrativeValidator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class OpenAiComparisonAdapter implements OpComparisonNarrativePort {

  private static final String SYSTEM_PROMPT =
      """
      너는 Suri-Map 수색 작전 차수 비교 근거를 지휘관이 읽기 쉬운 한국어 기록 차이 요약 JSON으로 옮긴다.
      입력 evidence에 있는 사실만 사용하고 원인, 의도, 전략, 위험도, 추천, 다음 차수 제안을 추론하지 않는다.
      금지 표현: 전략, 암묵지, 시사, 의미한다, ~로 보인다, 효율, 잘못, 더 나음, 추천, 다음 차수, 미수색, 위험, 가능성 높음.
      각 observation은 sentence와 factIds만 출력한다. factIds에는 입력 diffFacts 또는 regionFacts의 factId만 둔다.
      source, key, value, operationalPeriodId를 출력하지 않는다.
      """;

  private static final String RESPONSE_FORMAT_NAME = "op_comparison_observations";

  private final OpenAiComparisonProperties properties;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final OpComparisonNarrativeValidator narrativeValidator;

  OpenAiComparisonAdapter(
      OpenAiComparisonProperties properties,
      RestTemplate restTemplate,
      ObjectMapper objectMapper,
      OpComparisonNarrativeValidator narrativeValidator) {
    this.properties = properties;
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
    this.narrativeValidator = narrativeValidator;
  }

  @Override
  public OpComparisonNarrativeResult generate(OpComparisonNarrativeRequest request) {
    if (!properties.configured()) {
      return OpComparisonNarrativeResult.failed(OpComparisonNarrativeResult.PROVIDER_FAILURE);
    }

    Map<String, Object> body;
    try {
      body = requestBody(request);
    } catch (JsonProcessingException exception) {
      return OpComparisonNarrativeResult.failed(OpComparisonNarrativeResult.PROVIDER_FAILURE);
    }

    ResponseEntity<String> response;
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(properties.apiKey());
      response =
          restTemplate.exchange(
              properties.responsesUri(),
              HttpMethod.POST,
              new HttpEntity<>(body, headers),
              String.class);
    } catch (RestClientException | IllegalArgumentException exception) {
      return OpComparisonNarrativeResult.failed(OpComparisonNarrativeResult.PROVIDER_FAILURE);
    }

    try {
      String outputText = extractOutputText(response.getBody());
      if (outputText == null || outputText.isBlank()) {
        return OpComparisonNarrativeResult.failed(OpComparisonNarrativeResult.EMPTY_OUTPUT);
      }
      if (!isObservationJson(outputText)) {
        return OpComparisonNarrativeResult.failed(OpComparisonNarrativeResult.SCHEMA_INVALID);
      }
      OpComparisonNarrativeValidator.ValidationResult validation =
          narrativeValidator.validate(request.evidencePackage(), outputText);
      if (!validation.valid()) {
        return OpComparisonNarrativeResult.failed(validation.failureReason());
      }
      return OpComparisonNarrativeResult.ready(outputText);
    } catch (JsonProcessingException exception) {
      return OpComparisonNarrativeResult.failed(OpComparisonNarrativeResult.SCHEMA_INVALID);
    }
  }

  private Map<String, Object> requestBody(OpComparisonNarrativeRequest request)
      throws JsonProcessingException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", properties.model());
    body.put("store", false);
    body.put(
        "input",
        List.of(
            Map.of("role", "system", "content", SYSTEM_PROMPT),
            Map.of(
                "role",
                "user",
                "content",
                "다음 JSON evidence를 읽고 schema에 맞는 관찰 JSON만 생성한다.\n"
                    + objectMapper.writeValueAsString(request.evidencePackage()))));
    body.put("text", Map.of("format", responseFormat()));
    return body;
  }

  private Map<String, Object> responseFormat() {
    return Map.of(
        "type",
        "json_schema",
        "name",
        RESPONSE_FORMAT_NAME,
        "strict",
        true,
        "schema",
        observationSchema());
  }

  private Map<String, Object> observationSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of("observations", Map.of("type", "array", "items", observationItemSchema())),
        "required",
        List.of("observations"),
        "additionalProperties",
        false);
  }

  private Map<String, Object> observationItemSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "sentence", Map.of("type", "string"),
            "factIds", Map.of("type", "array", "items", Map.of("type", "string"))),
        "required",
        List.of("sentence", "factIds"),
        "additionalProperties",
        false);
  }

  private String extractOutputText(String responseBody) throws JsonProcessingException {
    if (responseBody == null || responseBody.isBlank()) {
      return null;
    }

    JsonNode root = objectMapper.readTree(responseBody);
    JsonNode sdkOutputText = root.get("output_text");
    if (sdkOutputText != null && sdkOutputText.isTextual()) {
      return sdkOutputText.asText();
    }

    List<String> parts = new ArrayList<>();
    for (JsonNode outputItem : root.path("output")) {
      for (JsonNode contentItem : outputItem.path("content")) {
        if ("output_text".equals(contentItem.path("type").asText())
            && contentItem.path("text").isTextual()) {
          parts.add(contentItem.path("text").asText());
        }
      }
    }
    return parts.isEmpty() ? null : String.join("", parts);
  }

  private boolean isObservationJson(String outputText) throws JsonProcessingException {
    if (outputText == null || outputText.isBlank()) {
      return false;
    }
    JsonNode root = objectMapper.readTree(outputText);
    return root.isObject() && root.path("observations").isArray();
  }
}
