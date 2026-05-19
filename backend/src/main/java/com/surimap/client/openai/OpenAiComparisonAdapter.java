package com.surimap.client.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.opcomparison.OpComparisonNarrativePort;
import com.surimap.opcomparison.OpComparisonNarrativeRequest;
import com.surimap.opcomparison.OpComparisonNarrativeResult;
import com.surimap.opcomparison.OpComparisonNarrativeValidator;
import com.surimap.opcomparison.OpComparisonEvidencePackage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
      metricLabel이 있으면 sentence에는 metricLabel의 한국어 표현을 사용하고 영문 필드명을 쓰지 않는다.
      sentence의 숫자는 cited factIds가 가리키는 fact 안의 숫자만 사용한다. 날짜/시각은 꼭 필요할 때만 입력 fact 그대로 쓴다.
      source, key, value, operationalPeriodId를 출력하지 않는다.
      """;

  private static final String RESPONSE_FORMAT_NAME = "op_comparison_observations";
  private static final Map<String, String> METRIC_LABELS =
      Map.of(
          "pathDistanceMeters", "이동 거리",
          "walkingDistanceMeters", "도보 이동 거리",
          "drivingDistanceMeters", "차량 이동 거리",
          "walkingRatioPercent", "도보 비율",
          "averageSpeedKmh", "평균 속도",
          "stoppedSegmentCount", "멈춘 구간 수",
          "stoppedDurationSeconds", "멈춘 시간",
          "markerCount", "마커 수",
          "handoverMemoCount", "인수인계 메모 수");

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
                    + objectMapper.writeValueAsString(providerEvidence(request.evidencePackage())))));
    body.put("text", Map.of("format", responseFormat()));
    return body;
  }

  private Map<String, Object> providerEvidence(OpComparisonEvidencePackage evidencePackage) {
    Map<UUID, Integer> sequenceByOpId = new LinkedHashMap<>();
    evidencePackage
        .operationalPeriods()
        .forEach(op -> sequenceByOpId.put(op.operationalPeriodId(), op.sequenceNumber()));

    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put(
        "operationalPeriods",
        evidencePackage.operationalPeriods().stream()
            .map(
                op -> {
                  Map<String, Object> item = new LinkedHashMap<>();
                  item.put("sequenceNumber", op.sequenceNumber());
                  return item;
                })
            .toList());
    evidence.put(
        "diffFacts",
        evidencePackage.diffFacts().stream()
            .map(
                fact -> {
                  Map<String, Object> item = new LinkedHashMap<>();
                  item.put("factId", fact.factId());
                  item.put("type", fact.type());
                  item.put(
                      "metricLabel",
                      METRIC_LABELS.getOrDefault(fact.metricKey(), fact.metricKey()));
                  item.put("leftSequenceNumber", sequenceByOpId.get(fact.leftOperationalPeriodId()));
                  item.put("rightSequenceNumber", sequenceByOpId.get(fact.rightOperationalPeriodId()));
                  item.put("leftValue", fact.leftValue());
                  item.put("rightValue", fact.rightValue());
                  item.put("delta", fact.delta());
                  item.put("threshold", fact.threshold());
                  return item;
                })
            .toList());
    evidence.put(
        "regionFacts",
        evidencePackage.regionFacts().stream()
            .map(
                fact -> {
                  Map<String, Object> item = new LinkedHashMap<>();
                  item.put("factId", fact.factId());
                  item.put("type", fact.type());
                  item.put(
                      "sequenceNumbers",
                      fact.operationalPeriodIds().stream().map(sequenceByOpId::get).toList());
                  item.put("areaSquareMeters", fact.areaSquareMeters());
                  item.put("firstPassTimes", sequencedMap(fact.firstPassTimes(), sequenceByOpId));
                  item.put("durationSeconds", sequencedMap(fact.durationSeconds(), sequenceByOpId));
                  return item;
                })
            .toList());
    return evidence;
  }

  private Map<String, Object> sequencedMap(Map<UUID, ?> values, Map<UUID, Integer> sequenceByOpId) {
    Map<String, Object> sequenced = new LinkedHashMap<>();
    values.forEach((opId, value) -> sequenced.put(String.valueOf(sequenceByOpId.get(opId)), value));
    return sequenced;
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
