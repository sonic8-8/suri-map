package com.surimap.opcomparison;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpComparisonNarrativeValidator {

  public static final List<String> FORBIDDEN_PHRASES =
      List.of(
          "전략", "암묵지", "시사", "의미한다", "로 보인다", "~로 보인다", "효율", "잘못", "더 나음", "추천", "다음 차수", "미수색",
          "위험", "가능성 높음", "왜", "의도", "방침");

  private static final Pattern NUMBER_PATTERN =
      Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?");

  private final ObjectMapper objectMapper;

  public OpComparisonNarrativeValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public boolean isValid(OpComparisonEvidencePackage evidencePackage, String observationsJson) {
    if (evidencePackage == null || observationsJson == null || observationsJson.isBlank()) {
      return false;
    }

    try {
      JsonNode root = objectMapper.readTree(observationsJson);
      JsonNode observations = root.path("observations");
      if (!root.isObject() || !observations.isArray() || observations.isEmpty()) {
        return false;
      }

      EvidenceIndex evidenceIndex = EvidenceIndex.from(evidencePackage);
      for (JsonNode observationNode : observations) {
        if (!isValidObservation(observationNode, evidenceIndex)) {
          return false;
        }
      }
      return true;
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      return false;
    }
  }

  public boolean containsForbiddenPhrase(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    return FORBIDDEN_PHRASES.stream().anyMatch(text::contains);
  }

  private boolean isValidObservation(JsonNode observationNode, EvidenceIndex evidenceIndex) {
    if (!observationNode.isObject()) {
      return false;
    }

    JsonNode observation = observationNode.path("observation");
    if (!observation.isTextual()
        || observation.asText().isBlank()
        || containsForbiddenPhrase(observation.asText())
        || containsUnsupportedNumber(observation.asText(), evidenceIndex.allowedNumbers())) {
      return false;
    }

    JsonNode evidenceItems = observationNode.path("evidence");
    if (!evidenceItems.isArray() || evidenceItems.isEmpty()) {
      return false;
    }
    for (JsonNode evidenceItem : evidenceItems) {
      if (!matchesEvidence(evidenceItem, evidenceIndex)) {
        return false;
      }
    }
    return true;
  }

  private boolean containsUnsupportedNumber(String observation, Set<String> allowedNumbers) {
    Matcher matcher = NUMBER_PATTERN.matcher(observation);
    while (matcher.find()) {
      if (isOrdinalSequenceNumber(observation, matcher.end())) {
        continue;
      }
      String normalized = normalizeNumber(matcher.group());
      if (normalized != null && !allowedNumbers.contains(normalized)) {
        return true;
      }
    }
    return false;
  }

  private boolean isOrdinalSequenceNumber(String text, int numberEnd) {
    return numberEnd < text.length() && text.charAt(numberEnd) == '차';
  }

  private boolean matchesEvidence(JsonNode evidenceItem, EvidenceIndex evidenceIndex) {
    if (!evidenceItem.isObject()) {
      return false;
    }

    String source = textField(evidenceItem, "source");
    String factId = textField(evidenceItem, "factId");
    String operationalPeriodId = textField(evidenceItem, "operationalPeriodId");
    String key = textField(evidenceItem, "key");
    String value = textField(evidenceItem, "value");
    if (source == null
        || factId == null
        || operationalPeriodId == null
        || key == null
        || value == null) {
      return false;
    }

    UUID opId = parseUuid(operationalPeriodId);
    if (opId == null) {
      return false;
    }

    return switch (source) {
      case "diffFact" -> matchesDiffFact(evidenceIndex, factId, opId, key, value);
      case "regionFact" -> matchesRegionFact(evidenceIndex, factId, opId, key, value);
      case "metric" -> matchesMetric(evidenceIndex, opId, key, value);
      default -> false;
    };
  }

  private boolean matchesDiffFact(
      EvidenceIndex evidenceIndex, String factId, UUID opId, String key, String value) {
    OpComparisonDiffFact fact = evidenceIndex.diffFacts().get(factId);
    if (fact == null
        || (!fact.leftOperationalPeriodId().equals(opId)
            && !fact.rightOperationalPeriodId().equals(opId))) {
      return false;
    }
    Object actual =
        switch (key) {
          case "type" -> fact.type();
          case "metricKey" -> fact.metricKey();
          case "leftOperationalPeriodId" -> fact.leftOperationalPeriodId();
          case "rightOperationalPeriodId" -> fact.rightOperationalPeriodId();
          case "leftValue" -> fact.leftValue();
          case "rightValue" -> fact.rightValue();
          case "delta" -> fact.delta();
          case "threshold" -> fact.threshold();
          default -> null;
        };
    return valueMatches(actual, value);
  }

  private boolean matchesRegionFact(
      EvidenceIndex evidenceIndex, String factId, UUID opId, String key, String value) {
    OpComparisonRegionEvidence fact = evidenceIndex.regionFacts().get(factId);
    if (fact == null || !fact.operationalPeriodIds().contains(opId)) {
      return false;
    }
    Object actual =
        switch (key) {
          case "type" -> fact.type();
          case "areaSquareMeters" -> fact.areaSquareMeters();
          case "firstPassTime" -> fact.firstPassTimes().get(opId);
          case "durationSeconds" -> fact.durationSeconds().get(opId);
          default -> null;
        };
    return valueMatches(actual, value);
  }

  private boolean matchesMetric(EvidenceIndex evidenceIndex, UUID opId, String key, String value) {
    OpComparisonOperationalPeriodEvidence op = evidenceIndex.operationalPeriods().get(opId);
    if (op == null) {
      return false;
    }
    Object actual =
        switch (key) {
          case "sequenceNumber" -> op.sequenceNumber();
          case "startedAt" -> op.startedAt();
          case "endedAt" -> op.endedAt();
          case "pathDistanceMeters" ->
              metricValue(op, OpComparisonMetricsEvidence::pathDistanceMeters);
          case "walkingDistanceMeters" ->
              metricValue(op, OpComparisonMetricsEvidence::walkingDistanceMeters);
          case "drivingDistanceMeters" ->
              metricValue(op, OpComparisonMetricsEvidence::drivingDistanceMeters);
          case "walkingRatioPercent" ->
              metricValue(op, OpComparisonMetricsEvidence::walkingRatioPercent);
          case "averageSpeedKmh" -> metricValue(op, OpComparisonMetricsEvidence::averageSpeedKmh);
          case "stoppedSegmentCount" ->
              metricValue(op, OpComparisonMetricsEvidence::stoppedSegmentCount);
          case "stoppedDurationSeconds" ->
              metricValue(op, OpComparisonMetricsEvidence::stoppedDurationSeconds);
          case "markerCount" -> metricValue(op, OpComparisonMetricsEvidence::markerCount);
          case "handoverMemoCount" ->
              metricValue(op, OpComparisonMetricsEvidence::handoverMemoCount);
          default -> null;
        };
    return valueMatches(actual, value);
  }

  private Object metricValue(
      OpComparisonOperationalPeriodEvidence op, MetricValueExtractor extractor) {
    if (op.metrics() == null) {
      return null;
    }
    return extractor.extract(op.metrics());
  }

  private boolean valueMatches(Object actual, String value) {
    if (actual == null || value == null) {
      return false;
    }
    if (actual instanceof Number || actual instanceof BigDecimal) {
      String actualNumber = normalizeNumber(actual.toString());
      String providedNumber = normalizeNumber(value);
      return actualNumber != null && actualNumber.equals(providedNumber);
    }
    return actual.toString().equals(value);
  }

  private static String textField(JsonNode node, String fieldName) {
    JsonNode field = node.path(fieldName);
    return field.isTextual() ? field.asText() : null;
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private static String normalizeNumber(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    Matcher matcher = NUMBER_PATTERN.matcher(value.replace(",", ""));
    if (!matcher.find()) {
      return null;
    }
    return new BigDecimal(matcher.group()).stripTrailingZeros().toPlainString();
  }

  private record EvidenceIndex(
      Map<String, OpComparisonDiffFact> diffFacts,
      Map<String, OpComparisonRegionEvidence> regionFacts,
      Map<UUID, OpComparisonOperationalPeriodEvidence> operationalPeriods,
      Set<String> allowedNumbers) {

    static EvidenceIndex from(OpComparisonEvidencePackage evidencePackage) {
      Map<String, OpComparisonDiffFact> diffFacts = new HashMap<>();
      Map<String, OpComparisonRegionEvidence> regionFacts = new HashMap<>();
      Map<UUID, OpComparisonOperationalPeriodEvidence> operationalPeriods = new HashMap<>();
      Set<String> allowedNumbers = new HashSet<>();

      evidencePackage.diffFacts().forEach(fact -> indexDiffFact(fact, diffFacts, allowedNumbers));
      evidencePackage
          .regionFacts()
          .forEach(fact -> indexRegionFact(fact, regionFacts, allowedNumbers));
      evidencePackage
          .operationalPeriods()
          .forEach(op -> indexOperationalPeriod(op, operationalPeriods, allowedNumbers));

      return new EvidenceIndex(diffFacts, regionFacts, operationalPeriods, allowedNumbers);
    }

    private static void indexDiffFact(
        OpComparisonDiffFact fact,
        Map<String, OpComparisonDiffFact> diffFacts,
        Set<String> allowedNumbers) {
      diffFacts.put(fact.factId(), fact);
      addNumber(allowedNumbers, fact.leftValue());
      addNumber(allowedNumbers, fact.rightValue());
      addNumber(allowedNumbers, fact.delta());
      addNumbersFromText(allowedNumbers, fact.threshold());
    }

    private static void indexRegionFact(
        OpComparisonRegionEvidence fact,
        Map<String, OpComparisonRegionEvidence> regionFacts,
        Set<String> allowedNumbers) {
      regionFacts.put(fact.factId(), fact);
      addNumber(allowedNumbers, fact.areaSquareMeters());
      fact.durationSeconds().values().forEach(duration -> addNumber(allowedNumbers, duration));
      fact.firstPassTimes()
          .values()
          .forEach(instant -> addNumbersFromText(allowedNumbers, instant));
    }

    private static void indexOperationalPeriod(
        OpComparisonOperationalPeriodEvidence op,
        Map<UUID, OpComparisonOperationalPeriodEvidence> operationalPeriods,
        Set<String> allowedNumbers) {
      operationalPeriods.put(op.operationalPeriodId(), op);
      addNumber(allowedNumbers, op.sequenceNumber());
      addNumbersFromText(allowedNumbers, op.startedAt());
      addNumbersFromText(allowedNumbers, op.endedAt());
      if (op.metrics() == null) {
        return;
      }
      OpComparisonMetricsEvidence metrics = op.metrics();
      addNumber(allowedNumbers, metrics.pathDistanceMeters());
      addNumber(allowedNumbers, metrics.walkingDistanceMeters());
      addNumber(allowedNumbers, metrics.drivingDistanceMeters());
      addNumber(allowedNumbers, metrics.walkingRatioPercent());
      addNumber(allowedNumbers, metrics.averageSpeedKmh());
      addNumber(allowedNumbers, metrics.stoppedSegmentCount());
      addNumber(allowedNumbers, metrics.stoppedDurationSeconds());
      addNumber(allowedNumbers, metrics.markerCount());
      addNumber(allowedNumbers, metrics.handoverMemoCount());
    }

    private static void addNumber(Set<String> allowedNumbers, Object value) {
      if (value != null) {
        String normalized = normalizeNumber(value.toString());
        if (normalized != null) {
          allowedNumbers.add(normalized);
        }
      }
    }

    private static void addNumbersFromText(Set<String> allowedNumbers, Object value) {
      if (value == null) {
        return;
      }
      Matcher matcher = NUMBER_PATTERN.matcher(value.toString());
      while (matcher.find()) {
        String normalized = normalizeNumber(matcher.group());
        if (normalized != null) {
          allowedNumbers.add(normalized);
        }
      }
    }
  }

  @FunctionalInterface
  private interface MetricValueExtractor {
    Object extract(OpComparisonMetricsEvidence metrics);
  }
}
