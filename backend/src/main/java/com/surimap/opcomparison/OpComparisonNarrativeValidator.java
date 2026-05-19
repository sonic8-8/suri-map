package com.surimap.opcomparison;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    return validate(evidencePackage, observationsJson).valid();
  }

  public ValidationResult validate(OpComparisonEvidencePackage evidencePackage, String observationsJson) {
    if (evidencePackage == null || observationsJson == null || observationsJson.isBlank()) {
      return ValidationResult.failed(OpComparisonNarrativeResult.EMPTY_OUTPUT);
    }

    try {
      JsonNode root = objectMapper.readTree(observationsJson);
      JsonNode observations = root.path("observations");
      if (!root.isObject() || root.size() != 1 || !observations.isArray()) {
        return ValidationResult.failed(OpComparisonNarrativeResult.SCHEMA_INVALID);
      }
      if (observations.isEmpty()) {
        return ValidationResult.failed(OpComparisonNarrativeResult.EMPTY_OUTPUT);
      }

      EvidenceIndex evidenceIndex = EvidenceIndex.from(evidencePackage);
      for (JsonNode observationNode : observations) {
        ValidationResult result = validateObservation(observationNode, evidenceIndex);
        if (!result.valid()) {
          return result;
        }
      }
      return ValidationResult.ok();
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      return ValidationResult.failed(OpComparisonNarrativeResult.SCHEMA_INVALID);
    }
  }

  public boolean containsForbiddenPhrase(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    return FORBIDDEN_PHRASES.stream().anyMatch(text::contains);
  }

  private ValidationResult validateObservation(JsonNode observationNode, EvidenceIndex evidenceIndex) {
    if (!observationNode.isObject() || observationNode.size() != 2) {
      return ValidationResult.failed(OpComparisonNarrativeResult.SCHEMA_INVALID);
    }

    JsonNode sentence = observationNode.path("sentence");
    if (!sentence.isTextual()) {
      return ValidationResult.failed(OpComparisonNarrativeResult.SCHEMA_INVALID);
    }
    if (sentence.asText().isBlank()) {
      return ValidationResult.failed(OpComparisonNarrativeResult.EMPTY_OUTPUT);
    }
    if (containsForbiddenPhrase(sentence.asText())) {
      return ValidationResult.failed(OpComparisonNarrativeResult.FORBIDDEN_PHRASE);
    }

    JsonNode factIds = observationNode.path("factIds");
    if (!factIds.isArray() || factIds.isEmpty()) {
      return ValidationResult.failed(OpComparisonNarrativeResult.SCHEMA_INVALID);
    }
    List<String> citedFactIds = new ArrayList<>();
    for (JsonNode factId : factIds) {
      if (!factId.isTextual() || factId.asText().isBlank()) {
        return ValidationResult.failed(OpComparisonNarrativeResult.SCHEMA_INVALID);
      }
      if (!evidenceIndex.supportsFactId(factId.asText())) {
        return ValidationResult.failed(OpComparisonNarrativeResult.UNSUPPORTED_FACT_ID);
      }
      citedFactIds.add(factId.asText());
    }
    if (containsUnsupportedNumber(sentence.asText(), evidenceIndex.allowedNumbersFor(citedFactIds))) {
      return ValidationResult.failed(OpComparisonNarrativeResult.VALIDATION_REJECTED);
    }
    return ValidationResult.ok();
  }

  private boolean containsUnsupportedNumber(String observation, Set<String> allowedNumbers) {
    Matcher matcher = NUMBER_PATTERN.matcher(observation);
    while (matcher.find()) {
      if (isOrdinalSequenceNumber(observation, matcher.end())
          || isOperationalPeriodLabelNumber(observation, matcher.start())) {
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

  private boolean isOperationalPeriodLabelNumber(String text, int numberStart) {
    int index = numberStart - 1;
    while (index >= 0 && Character.isWhitespace(text.charAt(index))) {
      index--;
    }
    return index >= 1 && text.charAt(index) == 'P' && text.charAt(index - 1) == 'O';
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
      Map<String, Set<String>> allowedNumbersByFactId) {

    boolean supportsFactId(String factId) {
      return diffFacts.containsKey(factId) || regionFacts.containsKey(factId);
    }

    Set<String> allowedNumbersFor(List<String> factIds) {
      Set<String> numbers = new HashSet<>();
      factIds.forEach(factId -> numbers.addAll(allowedNumbersByFactId.getOrDefault(factId, Set.of())));
      return numbers;
    }

    static EvidenceIndex from(OpComparisonEvidencePackage evidencePackage) {
      Map<String, OpComparisonDiffFact> diffFacts = new HashMap<>();
      Map<String, OpComparisonRegionEvidence> regionFacts = new HashMap<>();
      Map<String, Set<String>> allowedNumbersByFactId = new HashMap<>();

      evidencePackage.diffFacts().forEach(fact -> indexDiffFact(fact, diffFacts, allowedNumbersByFactId));
      evidencePackage
          .regionFacts()
          .forEach(fact -> indexRegionFact(fact, regionFacts, allowedNumbersByFactId));

      return new EvidenceIndex(diffFacts, regionFacts, allowedNumbersByFactId);
    }

    private static void indexDiffFact(
        OpComparisonDiffFact fact,
        Map<String, OpComparisonDiffFact> diffFacts,
        Map<String, Set<String>> allowedNumbersByFactId) {
      diffFacts.put(fact.factId(), fact);
      Set<String> allowedNumbers = new HashSet<>();
      addNumber(allowedNumbers, fact.leftValue());
      addNumber(allowedNumbers, fact.rightValue());
      addNumber(allowedNumbers, fact.delta());
      addNumbersFromText(allowedNumbers, fact.threshold());
      allowedNumbersByFactId.put(fact.factId(), Set.copyOf(allowedNumbers));
    }

    private static void indexRegionFact(
        OpComparisonRegionEvidence fact,
        Map<String, OpComparisonRegionEvidence> regionFacts,
        Map<String, Set<String>> allowedNumbersByFactId) {
      regionFacts.put(fact.factId(), fact);
      Set<String> allowedNumbers = new HashSet<>();
      addNumber(allowedNumbers, fact.areaSquareMeters());
      fact.durationSeconds().values().forEach(duration -> addNumber(allowedNumbers, duration));
      fact.firstPassTimes()
          .values()
          .forEach(instant -> addNumbersFromText(allowedNumbers, instant));
      allowedNumbersByFactId.put(fact.factId(), Set.copyOf(allowedNumbers));
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

  public record ValidationResult(boolean valid, String failureReason) {

    static ValidationResult ok() {
      return new ValidationResult(true, null);
    }

    static ValidationResult failed(String failureReason) {
      return new ValidationResult(false, failureReason);
    }
  }
}
