package com.surimap.incident.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.incident.fixture.IncidentSeedFixtureIds.SeedMarker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** L1 incident 테스트가 쓰는 공용 fixture ID 모음을 record로 묶어 돌려주는 로더. */
public final class IncidentSeedFixtureLoader {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String COMMON_FIXTURES_REL_PATH = "docs/spec/fixtures/common-fixtures.json";

  private IncidentSeedFixtureLoader() {}

  public static IncidentSeedFixtureIds loadPrecinctFirst() {
    JsonNode confirmed = required(readCommonFixtures(), "confirmed");
    JsonNode incidentSeed = required(confirmed, "incidentSeed");
    JsonNode seedIds = required(incidentSeed, "seedIds");
    JsonNode assignments = required(incidentSeed, "expectedIncidentAssignments");
    JsonNode mock112 = required(confirmed, "mock112SourceContract");

    return new IncidentSeedFixtureIds(
        requiredText(mock112, "sourceIncidentId"),
        requiredText(incidentSeed, "incidentId"),
        requiredText(incidentSeed, "currentOpId"),
        List.of(requiredText(seedIds, "markerId")),
        requiredText(seedIds, "pathVehicleId"),
        requiredText(seedIds, "pathFootId"),
        requiredText(seedIds, "memoId"),
        textList(required(incidentSeed, "accountIds")),
        textList(required(incidentSeed, "policePhoneIds")),
        textList(required(assignments, "beforeHandover")),
        textList(required(assignments, "afterHandoverAdded")),
        textList(required(assignments, "afterSupportAdded")),
        seedMarkers(required(mock112, "seedMarkers")));
  }

  private static JsonNode readCommonFixtures() {
    Path path = locateCommonFixtures();
    try {
      return MAPPER.readTree(path.toFile());
    } catch (IOException e) {
      throw new IllegalStateException("common-fixtures.json 읽기 실패 (경로: " + path + ")", e);
    }
  }

  private static Path locateCommonFixtures() {
    Path cursor = Path.of("").toAbsolutePath();
    while (cursor != null) {
      Path candidate = cursor.resolve(COMMON_FIXTURES_REL_PATH);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      cursor = cursor.getParent();
    }
    throw new IllegalStateException(
        "common-fixtures.json 파일을 찾지 못했습니다. 현재 폴더부터 부모 디렉토리를 거슬러 올라가며 탐색했으나 없음 (시작 경로: "
            + Path.of("").toAbsolutePath()
            + ")");
  }

  private static JsonNode required(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isMissingNode() || value.isNull()) {
      throw new IllegalStateException("필수 fixture 필드가 없습니다: " + field);
    }
    return value;
  }

  private static String requiredText(JsonNode node, String field) {
    JsonNode value = required(node, field);
    if (!value.isTextual()) {
      throw new IllegalStateException("fixture 필드는 문자열이어야 합니다: " + field);
    }
    return value.asText();
  }

  private static List<String> textList(JsonNode node) {
    if (!node.isArray()) {
      throw new IllegalStateException("fixture 필드는 배열이어야 합니다: " + node);
    }
    List<String> values = new ArrayList<>();
    for (JsonNode value : node) {
      if (!value.isTextual()) {
        throw new IllegalStateException("fixture 배열 원소는 문자열이어야 합니다: " + value);
      }
      values.add(value.asText());
    }
    return values;
  }

  private static List<SeedMarker> seedMarkers(JsonNode node) {
    if (!node.isArray()) {
      throw new IllegalStateException("seedMarkers는 배열이어야 합니다");
    }
    List<SeedMarker> markers = new ArrayList<>();
    for (JsonNode marker : node) {
      markers.add(
          new SeedMarker(
              requiredText(marker, "type"),
              requiredText(marker, "source"),
              requiredText(marker, "memo"),
              requiredNumber(marker, "lon"),
              requiredNumber(marker, "lat")));
    }
    return markers;
  }

  private static double requiredNumber(JsonNode node, String field) {
    JsonNode value = required(node, field);
    if (!value.isNumber()) {
      throw new IllegalStateException("fixture 필드는 숫자여야 합니다: " + field);
    }
    return value.asDouble();
  }
}
