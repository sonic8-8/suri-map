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
 * L2-T03 RED: heartbeat endpoint source contract.
 *
 * <p>The production endpoint does not exist yet, so these tests freeze the source-level contract
 * the coder must add: route, guard alias annotations, and canonical response fields.
 */
@DisplayName("L2-T03 police_phone heartbeat API RED")
class PolicePhoneHeartbeatApiRedTest {

  @Test
  @DisplayName("main source declares POST /api/police-phones/{policePhoneId}/heartbeat route")
  void main_source_declares_heartbeat_route() throws Exception {
    List<Path> candidates = findHeartbeatSourceFiles();

    assertThat(candidates)
        .as("heartbeat controller/service source must exist in src/main/java")
        .isNotEmpty();

    String source = Files.readString(candidates.get(0));
    assertThat(source).contains("/api/police-phones/{policePhoneId}/heartbeat");
  }

  @Test
  @DisplayName("heartbeat source declares app-police-phone guard chain for assigned vs unassigned behavior")
  void heartbeat_source_declares_app_police_phone_guard_chain() throws Exception {
    List<Path> candidates = findHeartbeatSourceFiles();

    assertThat(candidates)
        .as("heartbeat controller/service source must exist in src/main/java")
        .isNotEmpty();

    String source = Files.readString(candidates.get(0));
    assertThat(source)
        .contains("@RequirePolicePhone")
        .contains("@RequirePolicePhoneRegistered")
        .contains("@RequirePolicePhoneAssigned");
  }

  @Test
  @DisplayName("heartbeat source declares canonical response fields and ONLINE status")
  void heartbeat_source_declares_canonical_response_fields() throws Exception {
    List<Path> candidates = findHeartbeatSourceFiles();

    assertThat(candidates)
        .as("heartbeat controller/service source must exist in src/main/java")
        .isNotEmpty();

    String source = Files.readString(candidates.get(0));
    assertThat(source)
        .contains("ONLINE")
        .contains("policePhoneId")
        .contains("sequence")
        .contains("lastHeartbeatAt")
        .contains("lastSyncAt");
  }

  private static List<Path> findHeartbeatSourceFiles() throws IOException {
    try (Stream<Path> stream = Files.walk(Path.of("src/main/java"))) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .filter(PolicePhoneHeartbeatApiRedTest::containsHeartbeatTokens)
          .sorted(Comparator.naturalOrder())
          .toList();
    }
  }

  private static boolean containsHeartbeatTokens(Path path) {
    String fileName = path.getFileName().toString();
    return fileName.contains("Heartbeat") || fileName.equals("PolicePhoneController.java");
  }
}
