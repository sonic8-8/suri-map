package com.surimap.client.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.opcomparison.OpComparisonNarrativePort;
import com.surimap.opcomparison.OpComparisonNarrativeRequest;
import com.surimap.opcomparison.OpComparisonNarrativeResult;
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
      너는 Suri-Map 수색 작전 차수 비교 근거를 한국어 관찰 문장 JSON으로 옮긴다.
      입력 evidence에 있는 사실만 사용하고 원인, 의도, 전략, 위험도, 추천, 다음 차수 제안을 추론하지 않는다.
      금지 표현: 전략, 암묵지, 시사, 의미한다, ~로 보인다, 효율, 잘못, 더 나음, 추천, 다음 차수, 미수색, 위험, 가능성 높음.
      각 observation은 실제 evidence의 factId 또는 operationalPeriodId, key, value를 evidence 배열에 함께 둔다.
      """;

  private static final String RESPONSE_FORMAT_NAME = "op_comparison_observations";

  private final OpenAiComparisonProperties properties;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  OpenAiComparisonAdapter(
      OpenAiComparisonProperties properties, RestTemplate restTemplate, ObjectMapper objectMapper) {
    this.properties = properties;
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public OpComparisonNarrativeResult generate(OpComparisonNarrativeRequest request) {
    if (!properties.configured()) {
      return OpComparisonNarrativeResult.failed();
    }

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(properties.apiKey());
      ResponseEntity<String> response =
          restTemplate.exchange(
              properties.responsesUri(),
              HttpMethod.POST,
              new HttpEntity<>(requestBody(request), headers),
              String.class);
      String outputText = extractOutputText(response.getBody());
      if (!isObservationJson(outputText)) {
        return OpComparisonNarrativeResult.failed();
      }
      return OpComparisonNarrativeResult.ready(outputText);
    } catch (JsonProcessingException | RestClientException | IllegalArgumentException exception) {
      return OpComparisonNarrativeResult.failed();
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
            "observation", Map.of("type", "string"),
            "evidence", Map.of("type", "array", "items", evidenceItemSchema())),
        "required",
        List.of("observation", "evidence"),
        "additionalProperties",
        false);
  }

  private Map<String, Object> evidenceItemSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "source", Map.of("type", "string", "enum", List.of("diffFact", "regionFact", "metric")),
            "factId", Map.of("type", "string"),
            "operationalPeriodId", Map.of("type", "string"),
            "key", Map.of("type", "string"),
            "value", Map.of("type", "string")),
        "required",
        List.of("source", "factId", "operationalPeriodId", "key", "value"),
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
