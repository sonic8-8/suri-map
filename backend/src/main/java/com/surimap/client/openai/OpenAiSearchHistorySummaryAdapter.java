package com.surimap.client.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.domain.summary.ForbiddenSummaryGuard;
import com.surimap.domain.summary.SearchHistorySummaryPort;
import com.surimap.domain.summary.SummaryEvidence;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class OpenAiSearchHistorySummaryAdapter implements SearchHistorySummaryPort {

  private static final String SYSTEM_PROMPT =
      """
      너는 Suri-Map 수색 이력 evidence를 한국어 인수인계 요약 JSON으로 옮긴다.
      입력 evidence에 있는 사실만 사용하고 추천, 다음 구역, 미수색 확정, 위험 판단, 전략, 의도, 효율 판단을 추론하지 않는다.
      출력 summary는 4~7문장 한국어 산문 한 단락이다.
      허용 사실은 시간순 event, 경로/구역/마커/메모 count, type/tag count, source readiness뿐이다.
      입력에 없는 숫자, 장소명, 사람 이름 외 PII, source prompt를 출력하지 않는다.
      """;

  private static final String RESPONSE_FORMAT_NAME = "search_history_summary";
  private static final Set<String> SENSITIVE_FIELD_NAMES =
      Set.of(
          "lat",
          "lng",
          "lon",
          "latitude",
          "longitude",
          "coordinates",
          "geometry",
          "accountid",
          "requestedbyaccountid",
          "policephoneid",
          "phoneid",
          "photourl",
          "photoobjectkey",
          "uploadurl");
  private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
  private static final Pattern SENTENCE_TERMINATOR_PATTERN = Pattern.compile("[.!?。！？]+");
  private static final Pattern SENSITIVE_TEXT_KEY_PATTERN =
      Pattern.compile(
          "(?i)\\b(lat|lng|lon|latitude|longitude|accountId|account_id|requestedByAccountId|"
              + "requested_by_account_id|policePhoneId|police_phone_id|phoneId|phone_id|"
              + "photoUrl|photo_url|photoObjectKey|photo_object_key|uploadUrl|upload_url)"
              + "\\s*[:=]\\s*[^,\\s|\\n}]+");

  private final OpenAiComparisonProperties properties;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final ForbiddenSummaryGuard forbiddenSummaryGuard;

  OpenAiSearchHistorySummaryAdapter(
      OpenAiComparisonProperties properties,
      RestTemplate restTemplate,
      ObjectMapper objectMapper,
      ForbiddenSummaryGuard forbiddenSummaryGuard) {
    this.properties = properties;
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
    this.forbiddenSummaryGuard = forbiddenSummaryGuard;
  }

  @Override
  public SummaryResult generate(SummaryRequest request) {
    if (!properties.configured()) {
      return SummaryResult.failed();
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
      String summary = extractSummary(outputText);
      if (summary == null
          || summary.isBlank()
          || !hasAllowedSentenceCount(summary)
          || forbiddenSummaryGuard.containsForbiddenPhrase(summary)) {
        return SummaryResult.failed();
      }
      return SummaryResult.ready(summary);
    } catch (JsonProcessingException | RestClientException | IllegalArgumentException exception) {
      return SummaryResult.failed();
    }
  }

  private Map<String, Object> requestBody(SummaryRequest request) throws JsonProcessingException {
    SummaryEvidence evidence =
        new SummaryEvidence(
            request.summaryId(),
            request.operationalPeriodId(),
            request.incidentId(),
            sanitizedEvidenceSource(request.evidence()));
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
                "다음 JSON evidence를 읽고 schema에 맞는 요약 JSON만 생성한다.\n"
                    + objectMapper.writeValueAsString(evidence))));
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
        summarySchema());
  }

  private Map<String, Object> summarySchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of("summary", Map.of("type", "string")),
        "required",
        List.of("summary"),
        "additionalProperties",
        false);
  }

  private Object sanitizedEvidenceSource(String evidence) {
    if (evidence == null || evidence.isBlank()) {
      return Map.of("sourceText", "");
    }
    try {
      return sanitizeNode(objectMapper.readTree(evidence));
    } catch (JsonProcessingException exception) {
      return Map.of("sourceText", sanitizeText(evidence));
    }
  }

  private Object sanitizeNode(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isObject()) {
      Map<String, Object> values = new LinkedHashMap<>();
      Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (!isSensitiveField(field.getKey())) {
          values.put(field.getKey(), sanitizeNode(field.getValue()));
        }
      }
      return values;
    }
    if (node.isArray()) {
      List<Object> values = new ArrayList<>();
      node.forEach(item -> values.add(sanitizeNode(item)));
      return values;
    }
    if (node.isTextual()) {
      return sanitizeText(node.asText());
    }
    if (node.isNumber()) {
      return node.numberValue();
    }
    if (node.isBoolean()) {
      return node.booleanValue();
    }
    return node.asText();
  }

  private boolean isSensitiveField(String fieldName) {
    String normalized = fieldName.toLowerCase().replace("_", "").replace("-", "");
    return SENSITIVE_FIELD_NAMES.contains(normalized);
  }

  private String sanitizeText(String value) {
    String withoutUrls = URL_PATTERN.matcher(value).replaceAll("<redacted-url>");
    return SENSITIVE_TEXT_KEY_PATTERN.matcher(withoutUrls).replaceAll("$1=<redacted>");
  }

  private boolean hasAllowedSentenceCount(String summary) {
    long sentenceCount = SENTENCE_TERMINATOR_PATTERN.matcher(summary).results().count();
    return sentenceCount >= 4 && sentenceCount <= 7;
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

  private String extractSummary(String outputText) throws JsonProcessingException {
    if (outputText == null || outputText.isBlank()) {
      return null;
    }
    JsonNode root = objectMapper.readTree(outputText);
    if (!root.isObject() || root.size() != 1 || !root.path("summary").isTextual()) {
      return null;
    }
    return root.path("summary").asText();
  }
}
