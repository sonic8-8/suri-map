package com.surimap.opcomparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record OpComparisonRegionFactRow(
    String factId,
    String type,
    String operationalPeriodIds,
    String geometryGeojson,
    BigDecimal areaSquareMeters,
    String occupanciesJson) {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  OpComparisonRegionFact toFact() {
    return new OpComparisonRegionFact(
        factId,
        OpComparisonRegionFactType.valueOf(type),
        parseOperationalPeriodIds(),
        geometryGeojson,
        areaSquareMeters,
        parseOccupancies());
  }

  private List<UUID> parseOperationalPeriodIds() {
    if (operationalPeriodIds == null || operationalPeriodIds.isBlank()) {
      return List.of();
    }
    return Arrays.stream(operationalPeriodIds.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(UUID::fromString)
        .toList();
  }

  private List<OpComparisonRegionOccupancy> parseOccupancies() {
    if (occupanciesJson == null || occupanciesJson.isBlank()) {
      return List.of();
    }
    try {
      JsonNode root = OBJECT_MAPPER.readTree(occupanciesJson);
      List<OpComparisonRegionOccupancy> occupancies = new ArrayList<>();
      for (JsonNode node : root) {
        occupancies.add(
            new OpComparisonRegionOccupancy(
                UUID.fromString(node.path("operationalPeriodId").asText()),
                OffsetDateTime.parse(node.path("firstObservedAt").asText()).toInstant(),
                OffsetDateTime.parse(node.path("lastObservedAt").asText()).toInstant(),
                node.path("durationSeconds").asLong()));
      }
      return List.copyOf(occupancies);
    } catch (IOException e) {
      throw new IllegalStateException("OP comparison region occupancy JSON parse failed", e);
    }
  }
}
