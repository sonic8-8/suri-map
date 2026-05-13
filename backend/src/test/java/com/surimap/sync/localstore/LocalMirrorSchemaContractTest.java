package com.surimap.sync.localstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalMirrorSchemaContractTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Path S6_SPEC =
      Path.of("..", "docs", "spec", "specs", "S6.json").normalize();
  private static final Path COMMON_FIXTURES =
      Path.of("..", "docs", "spec", "fixtures", "common-fixtures.json").normalize();

  @Test
  void s6DefinesAndroidLocalStoreSchemas() {
    JsonNode localSchemas = findFieldRecursive(s6Root(), "local_schemas");
    assertThat(localSchemas).isNotNull();

    assertThat(findSchema(localSchemas, "android_outbox_row")).isNotNull();
    assertThat(findSchema(localSchemas, "android_sync_status")).isNotNull();
  }

  @Test
  void replayDomainOperationsCoverPathMarkerAndOfflinePackageMirrorFlow() {
    JsonNode replay = required(commonFixtureConfirmed(), "outboxReplay");
    JsonNode path = required(replay, "sc05PathReplay");
    JsonNode markerPhoto = required(replay, "sc06MarkerPhotoReplay");
    JsonNode packageReplay = required(replay, "sc09PackageReplay");

    assertThat(required(path, "dependencyGroup").asText()).isEqualTo("PATH");
    assertThat(required(markerPhoto, "markerOperationAlias").asText())
        .isEqualTo("op-outbox-marker-001");
    assertThat(required(markerPhoto, "markerOperationId").asText())
        .isEqualTo("66666666-0000-4000-8000-000000000601");
    assertThat(required(markerPhoto, "photoOperationAlias").asText())
        .isEqualTo("op-outbox-photo-001");
    assertThat(required(markerPhoto, "photoOperationId").asText())
        .isEqualTo("66666666-0000-4000-8000-000000000602");
    assertThat(required(packageReplay, "dependencyGroup").asText()).isEqualTo("PACKAGE_INSTALLATION");
    assertThat(required(required(packageReplay, "writeOperation"), "entityType").asText())
        .isEqualTo("offline_package_installation");
  }

  @Test
  void offlineWriteDefaultsToLocalMirrorPendingStatesBeforeReplay() {
    JsonNode networkScripts = required(commonFixtureConfirmed(), "networkScripts");
    JsonNode domainWriteExpectations = required(networkScripts, "domainWriteExpectations");

    assertThat(required(domainWriteExpectations, "offlineFailureLocalStatus").asText())
        .isEqualTo("PENDING_LOCAL");
    assertThat(required(domainWriteExpectations, "offlineFailureOutboxStatus").asText())
        .isEqualTo("PENDING_SEND");
  }

  @Test
  void localWriteFlowDefersServerWriteUntilReplay() {
    JsonNode syncFlowNode = findFieldRecursive(s6Root(), "sync_flow");
    assertThat(syncFlowNode).isNotNull();

    String syncFlow = syncFlowNode.asText();
    int localWriteIndex = syncFlow.indexOf("Local write");
    int outboxPendingIndex = syncFlow.indexOf("OutboxEntity PENDING");
    int serverWriteIndex = syncFlow.indexOf("server write/replay");

    assertThat(localWriteIndex).isGreaterThanOrEqualTo(0);
    assertThat(outboxPendingIndex).isGreaterThan(localWriteIndex);
    assertThat(serverWriteIndex).isGreaterThan(outboxPendingIndex);
  }

  private static JsonNode s6Root() {
    return readJson(S6_SPEC);
  }

  private static JsonNode commonFixtureConfirmed() {
    return required(readJson(COMMON_FIXTURES), "confirmed");
  }

  private static JsonNode findSchema(JsonNode schemas, String name) {
    for (JsonNode schema : schemas) {
      if (name.equals(required(schema, "name").asText())) {
        return schema;
      }
    }
    return null;
  }

  private static JsonNode findFieldRecursive(JsonNode node, String fieldName) {
    if (node == null) {
      return null;
    }
    JsonNode direct = node.get(fieldName);
    if (direct != null && !direct.isMissingNode()) {
      return direct;
    }
    if (node.isObject()) {
      var iterator = node.fields();
      while (iterator.hasNext()) {
        var entry = iterator.next();
        JsonNode found = findFieldRecursive(entry.getValue(), fieldName);
        if (found != null) {
          return found;
        }
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        JsonNode found = findFieldRecursive(child, fieldName);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private static JsonNode readJson(Path path) {
    try (var input = Files.newInputStream(path)) {
      return OBJECT_MAPPER.readTree(input);
    } catch (IOException exception) {
      throw new UncheckedIOException("Unable to read " + path.toAbsolutePath(), exception);
    }
  }

  private static JsonNode required(JsonNode node, String... fields) {
    JsonNode cursor = node;
    for (String field : fields) {
      cursor = cursor.get(field);
      if (cursor == null || cursor.isMissingNode()) {
        throw new IllegalStateException("Missing fixture/spec field: " + String.join(".", fields));
      }
    }
    return cursor;
  }
}
