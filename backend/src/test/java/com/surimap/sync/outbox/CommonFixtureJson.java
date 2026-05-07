package com.surimap.sync.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class CommonFixtureJson {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Path COMMON_FIXTURES =
      Path.of("..", "docs", "spec", "fixtures", "common-fixtures.json").normalize();

  private CommonFixtureJson() {}

  static JsonNode root() {
    try (var input = Files.newInputStream(COMMON_FIXTURES)) {
      return OBJECT_MAPPER.readTree(input);
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Unable to read " + COMMON_FIXTURES.toAbsolutePath(), exception);
    }
  }

  static JsonNode required(JsonNode node, String field) {
    var child = node.get(field);
    if (child == null || child.isMissingNode()) {
      throw new IllegalStateException("Missing fixture field: " + field);
    }
    return child;
  }
}
