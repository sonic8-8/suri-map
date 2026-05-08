package com.surimap.incident.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** L1-T02가 소비 Lane에 제공하는 OPEN/terminal 상태 fixture 로더. */
public final class IncidentLifecycleFixtureLoader {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String COMMON_FIXTURES_REL_PATH = "docs/spec/fixtures/common-fixtures.json";

  private IncidentLifecycleFixtureLoader() {}

  public static IncidentLifecycleFixtureStates loadTerminalStateRules() {
    JsonNode terminalStateRules = required(required(readCommonFixtures(), "confirmed"), "terminalStateRules");
    JsonNode requeue = required(terminalStateRules, "sc12CloseRequeue");

    return new IncidentLifecycleFixtureStates(
        requiredText(requeue, "incidentId"),
        requiredText(requeue, "preCloseOperationId"),
        requiredText(requeue, "postCloseOperationId"),
        requiredText(requeue, "expectedPreClose"),
        requiredText(requeue, "expectedPostClose"),
        requiredText(requeue, "expectedUserCategory"),
        failureCategoryRows(required(terminalStateRules, "failureCategoryRows")));
  }

  private static List<IncidentLifecycleFixtureStates.FailureCategoryRow> failureCategoryRows(
      JsonNode rows) {
    if (!rows.isArray()) {
      throw new IllegalStateException("failureCategoryRows는 배열이어야 합니다");
    }
    List<IncidentLifecycleFixtureStates.FailureCategoryRow> values = new ArrayList<>();
    for (JsonNode row : rows) {
      values.add(
          new IncidentLifecycleFixtureStates.FailureCategoryRow(
              requiredText(row, "operationId"),
              requiredText(row, "lastError"),
              requiredText(row, "userSafeFailureCategory"),
              requiredBoolean(row, "retryable")));
    }
    return values;
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
    throw new IllegalStateException("common-fixtures.json 파일을 찾지 못했습니다");
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

  private static boolean requiredBoolean(JsonNode node, String field) {
    JsonNode value = required(node, field);
    if (!value.isBoolean()) {
      throw new IllegalStateException("fixture 필드는 boolean이어야 합니다: " + field);
    }
    return value.asBoolean();
  }
}
