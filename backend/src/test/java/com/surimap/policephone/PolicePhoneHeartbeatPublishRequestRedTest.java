package com.surimap.policephone;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L2-T03 RED: POLICE_PHONE_HEARTBEAT_UPDATED publish request contract.
 *
 * <p>S1-2 owns the payload semantics and must hand the canonical fields to S4 EventHub.publish.
 * This test stays in test scope and fails until the production source introduces the task-specific
 * heartbeat publish request.
 */
@DisplayName("L2-T03 POLICE_PHONE_HEARTBEAT_UPDATED publish request RED")
class PolicePhoneHeartbeatPublishRequestRedTest {

  @Test
  @DisplayName("main source declares POLICE_PHONE_HEARTBEAT_UPDATED publish request")
  void main_source_declares_police_phone_heartbeat_updated_publish_request() throws Exception {
    List<Path> candidates = findMainSourceFiles("Heartbeat", "PublishRequest");

    assertThat(candidates)
        .as("heartbeat publish request source file must exist in src/main/java")
        .isNotEmpty();

    String source = Files.readString(candidates.get(0));
    assertThat(source).contains("POLICE_PHONE_HEARTBEAT_UPDATED");
  }

  @Test
  @DisplayName("heartbeat publish request payload exposes canonical id status version policePhoneId sequence fields")
  void heartbeat_publish_request_payload_exposes_canonical_fields() throws Exception {
    List<Path> candidates = findMainSourceFiles("Heartbeat", "PublishRequest");

    assertThat(candidates)
        .as("heartbeat publish request source file must exist in src/main/java")
        .isNotEmpty();

    String source = Files.readString(candidates.get(0));
    assertThat(source)
        .contains("id")
        .contains("status")
        .contains("version")
        .contains("policePhoneId")
        .contains("sequence")
        .contains("lastHeartbeatAt");
  }

  @Test
  @DisplayName("heartbeat publish request payload keeps optional lastSyncAt and ONLINE status")
  void heartbeat_publish_request_payload_keeps_last_sync_at_and_online_status()
      throws Exception {
    List<Path> candidates = findMainSourceFiles("Heartbeat", "PublishRequest");

    assertThat(candidates)
        .as("heartbeat publish request source file must exist in src/main/java")
        .isNotEmpty();

    String source = Files.readString(candidates.get(0));
    assertThat(source).contains("lastSyncAt").contains("ONLINE");
  }

  private static List<Path> findMainSourceFiles(String... nameTokens) throws IOException {
    try (Stream<Path> stream = Files.walk(Path.of("src/main/java"))) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .filter(path -> containsAllTokens(path.getFileName().toString(), nameTokens))
          .sorted(Comparator.naturalOrder())
          .toList();
    }
  }

  private static boolean containsAllTokens(String fileName, String... tokens) {
    for (String token : tokens) {
      if (!fileName.contains(token)) {
        return false;
      }
    }
    return true;
  }
}
