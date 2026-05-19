package com.surimap.api.controller.opcomparison.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.opcomparison.OpComparisonAnalysisRecord;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpComparisonResponse(
    UUID comparisonId,
    UUID incidentId,
    List<UUID> operationalPeriodIds,
    String sourceHash,
    String status,
    String narrativeStatus,
    JsonNode metrics,
    JsonNode diffFacts,
    JsonNode regionFacts,
    JsonNode observations,
    String failureReason,
    Instant requestedAt,
    Instant generatedAt,
    long version) {

  public OpComparisonResponse {
    operationalPeriodIds = List.copyOf(operationalPeriodIds);
  }

  public static OpComparisonResponse from(OpComparisonAnalysisRecord record, ObjectMapper mapper) {
    return new OpComparisonResponse(
        record.id(),
        record.incidentId(),
        parseOpIds(record.operationalPeriodIdsJson(), mapper),
        record.sourceDataHash(),
        record.status().name(),
        record.narrativeStatus().name(),
        parseJson(record.metricsJson(), mapper),
        parseJson(record.diffFactsJson(), mapper),
        parseJson(record.commonRegionsGeojson(), mapper),
        parseNullableJson(record.observationsJson(), mapper),
        record.failureReason(),
        record.requestedAt(),
        record.generatedAt(),
        record.version());
  }

  private static List<UUID> parseOpIds(String value, ObjectMapper mapper) {
    JsonNode root = parseJson(value, mapper);
    List<UUID> ids = new ArrayList<>();
    for (JsonNode node : root) {
      ids.add(UUID.fromString(node.asText()));
    }
    return List.copyOf(ids);
  }

  private static JsonNode parseNullableJson(String value, ObjectMapper mapper) {
    return value == null ? null : parseJson(value, mapper);
  }

  private static JsonNode parseJson(String value, ObjectMapper mapper) {
    try {
      return mapper.readTree(value == null || value.isBlank() ? "[]" : value);
    } catch (IOException exception) {
      throw new IllegalStateException("OP comparison JSON payload cannot be parsed", exception);
    }
  }
}
