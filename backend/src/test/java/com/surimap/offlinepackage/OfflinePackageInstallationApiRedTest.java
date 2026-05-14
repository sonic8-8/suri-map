package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.eventhub.adapter.MockEventHub;
import com.surimap.offlinepackage.fixture.OfflinePackageInstallationFixtures;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures;
import com.surimap.offlinepackage.query.OfflinePackageInstallationQuery;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** L6-T06A RED tests for S7 offline_package_installation API and event publication. */
@SpringBootTest(properties = "tileserver.mode=fixture")
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("L6-T06A offline_package_installation API/event RED")
class OfflinePackageInstallationApiRedTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private MockEventHub eventHub;

  @Autowired private OfflinePackageInstallationQuery installationQuery;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void resetEventHub() {
    eventHub.reset();
  }

  @Test
  @DisplayName("manifest tileItems expose checksums aligned with local tile fixture blobs")
  void manifest_tile_items_expose_checksums_aligned_with_local_tile_fixture_blobs()
      throws Exception {
    String body =
        mockMvc
            .perform(
                get(OfflinePackageInstallationFixtures.manifestApiPath())
                    .header("Authorization", "Bearer app-package-session")
                    .header("X-Client-Channel", "APP")
                    .header("X-PolicePhone-Id", OfflinePackageManifestFixtures.POLICE_PHONE_ID)
                    .param("policePhoneId", OfflinePackageManifestFixtures.POLICE_PHONE_ID))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Map<String, OfflinePackageManifestFixtures.TileItem> expectedTiles =
        OfflinePackageManifestFixtures.tileManifest().tiles().stream()
            .collect(
                Collectors.toMap(
                    OfflinePackageManifestFixtures.TileItem::itemKey, Function.identity()));

    JsonNode tileItems = objectMapper.readTree(body).path("tileItems");

    assertThat(tileItems).hasSize(expectedTiles.size());
    tileItems.forEach(
        tile -> {
          OfflinePackageManifestFixtures.TileItem expected =
              expectedTiles.get(tile.path("itemKey").asText());
          assertThat(expected).as(tile.toString()).isNotNull();
          assertThat(tile.path("checksum").asText()).isEqualTo(expected.checksum());
          assertThat(tile.path("bytes").asInt()).isEqualTo(expected.bytes());
        });
  }

  @Test
  @DisplayName("PARTIAL status report updates manifest package item states")
  void partial_status_report_updates_manifest_package_item_states() throws Exception {
    mockMvc
        .perform(
            post(OfflinePackageInstallationFixtures.apiPath())
                .header("Authorization", "Bearer app-package-session")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", OfflinePackageManifestFixtures.POLICE_PHONE_ID)
                .header("Idempotency-Key", "idem-package-partial-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OfflinePackageInstallationFixtures.partialReportJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(OfflinePackageInstallationFixtures.INSTALLATION_ID)))
        .andExpect(jsonPath("$.status", is("PARTIAL")))
        .andExpect(jsonPath("$.version", is(OfflinePackageInstallationFixtures.VERSION)))
        .andExpect(
            jsonPath("$.manifestVersion", is(OfflinePackageManifestFixtures.MANIFEST_VERSION)))
        .andExpect(jsonPath("$.readyForOfflineUse", is(false)))
        .andExpect(jsonPath("$.serverTs").exists());

    mockMvc
        .perform(
            get(OfflinePackageInstallationFixtures.manifestApiPath())
                .header("Authorization", "Bearer app-package-session")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", OfflinePackageManifestFixtures.POLICE_PHONE_ID)
                .param("policePhoneId", OfflinePackageManifestFixtures.POLICE_PHONE_ID)
                .param(
                    "knownManifestRevision",
                    String.valueOf(OfflinePackageManifestFixtures.MANIFEST_VERSION)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.packageItems[?(@.itemKey == '%s')].status"
                    .formatted(OfflinePackageInstallationFixtures.INITIAL_MARKER_ITEM_KEY),
                contains("FAILED")))
        .andExpect(
            jsonPath(
                "$.packageItems[?(@.itemKey == '%s')].status"
                    .formatted(OfflinePackageInstallationFixtures.TILE_ITEM_KEY),
                contains("FAILED")))
        .andExpect(
            jsonPath("$.packageItems[?(@.itemKey == 'incident:inc-precinct-first-001')].status")
                .value(contains("DOWNLOADED")));
  }

  @Test
  @DisplayName("READY status report publishes OFFLINE_PACKAGE_INSTALLATION_CHANGED")
  void ready_status_report_publishes_offline_package_installation_changed() throws Exception {
    mockMvc
        .perform(
            post(OfflinePackageInstallationFixtures.apiPath())
                .header("Authorization", "Bearer app-package-session")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", OfflinePackageManifestFixtures.POLICE_PHONE_ID)
                .header("Idempotency-Key", OfflinePackageInstallationFixtures.IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OfflinePackageInstallationFixtures.readyReportJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(OfflinePackageInstallationFixtures.INSTALLATION_ID)))
        .andExpect(jsonPath("$.status", is("READY")))
        .andExpect(jsonPath("$.version", is(OfflinePackageInstallationFixtures.VERSION)))
        .andExpect(
            jsonPath("$.manifestVersion", is(OfflinePackageManifestFixtures.MANIFEST_VERSION)))
        .andExpect(jsonPath("$.readyForOfflineUse", is(true)));

    assertThat(eventHub.findByType(OfflinePackageInstallationFixtures.EVENT_TYPE))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.type()).isEqualTo(OfflinePackageInstallationFixtures.EVENT_TYPE);
              assertThat(event.payloadFormatVersion()).isEqualTo(1);
              assertThat(event.sourceEntityType())
                  .isEqualTo(OfflinePackageInstallationFixtures.SOURCE_ENTITY_TYPE);
              assertThat(event.payload())
                  .containsEntry("id", OfflinePackageInstallationFixtures.INSTALLATION_ID)
                  .containsEntry("status", "READY")
                  .containsEntry("version", (long) OfflinePackageInstallationFixtures.VERSION)
                  .containsEntry("incidentId", OfflinePackageManifestFixtures.INCIDENT_ID)
                  .containsEntry("policePhoneId", OfflinePackageManifestFixtures.POLICE_PHONE_ID)
                  .containsEntry("manifestVersion", OfflinePackageManifestFixtures.MANIFEST_VERSION)
                  .containsEntry("sequence", (long) OfflinePackageInstallationFixtures.SEQUENCE);
            });

    assertThat(installationQuery.byIncident(OfflinePackageManifestFixtures.INCIDENT_ID))
        .anySatisfy(
            row -> {
              assertThat(row.id()).isEqualTo(OfflinePackageInstallationFixtures.INSTALLATION_ID);
              assertThat(row.status()).isEqualTo("READY");
              assertThat(row.version()).isEqualTo(OfflinePackageInstallationFixtures.VERSION);
              assertThat(row.sequence()).isEqualTo(OfflinePackageInstallationFixtures.SEQUENCE);
              assertThat(row.manifestVersion())
                  .isEqualTo(OfflinePackageManifestFixtures.MANIFEST_VERSION);
              assertThat(row.readyForOfflineUse()).isTrue();
            });
  }

  @Test
  @DisplayName("same manifest accepts installation reports from two PolicePhones")
  void same_manifest_accepts_reports_from_two_police_phones() throws Exception {
    mockMvc
        .perform(
            post(OfflinePackageInstallationFixtures.apiPath())
                .header("Authorization", "Bearer app-package-session")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", OfflinePackageManifestFixtures.POLICE_PHONE_ID)
                .header("Idempotency-Key", "idem-package-phone-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OfflinePackageInstallationFixtures.readyReportJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(OfflinePackageInstallationFixtures.INSTALLATION_ID)));

    String phone02ReadyReport =
        OfflinePackageInstallationFixtures.readyReportJson()
            .replace(
                OfflinePackageManifestFixtures.POLICE_PHONE_ID,
                OfflinePackageInstallationFixtures.SEEDED_PHONE_02_ID)
            .replace(
                "\"sequence\": %d".formatted(OfflinePackageInstallationFixtures.SEQUENCE),
                "\"sequence\": %d".formatted(OfflinePackageInstallationFixtures.SEQUENCE + 1));

    mockMvc
        .perform(
            post(OfflinePackageInstallationFixtures.apiPath())
                .header("Authorization", "Bearer app-package-session")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", OfflinePackageInstallationFixtures.SEEDED_PHONE_02_ID)
                .header("Idempotency-Key", "idem-package-phone-02")
                .contentType(MediaType.APPLICATION_JSON)
                .content(phone02ReadyReport))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("READY")));

    assertThat(installationQuery.byIncident(OfflinePackageManifestFixtures.INCIDENT_ID))
        .anySatisfy(
            row -> {
              assertThat(row.id()).isEqualTo(OfflinePackageInstallationFixtures.INSTALLATION_ID);
              assertThat(row.policePhoneId())
                  .isEqualTo(OfflinePackageManifestFixtures.POLICE_PHONE_ID);
            })
        .anySatisfy(
            row -> {
              assertThat(row.id()).isNotEqualTo(OfflinePackageInstallationFixtures.INSTALLATION_ID);
              assertThat(row.policePhoneId())
                  .isEqualTo(OfflinePackageInstallationFixtures.SEEDED_PHONE_02_ID);
              assertThat(row.status()).isEqualTo("READY");
            });
  }

  @Test
  @DisplayName("WEB channel status write is rejected before state mutation")
  void web_channel_status_write_is_rejected() throws Exception {
    mockMvc
        .perform(
            post(OfflinePackageInstallationFixtures.apiPath())
                .header("Authorization", "Bearer web-package-session")
                .header("X-Client-Channel", "WEB")
                .header("X-PolicePhone-Id", OfflinePackageManifestFixtures.POLICE_PHONE_ID)
                .header("Idempotency-Key", "idem-package-web-rejected")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OfflinePackageInstallationFixtures.readyReportJson()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    assertThat(eventHub.findByType(OfflinePackageInstallationFixtures.EVENT_TYPE)).isEmpty();
  }

  @Test
  @DisplayName("missing Idempotency-Key status write is rejected")
  void missing_idempotency_key_is_rejected() throws Exception {
    mockMvc
        .perform(
            post(OfflinePackageInstallationFixtures.apiPath())
                .header("Authorization", "Bearer app-package-session")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", OfflinePackageManifestFixtures.POLICE_PHONE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OfflinePackageInstallationFixtures.readyReportJson()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));
  }

  @Test
  @DisplayName("missing Authorization status write is rejected")
  void missing_authorization_is_rejected() throws Exception {
    mockMvc
        .perform(
            post(OfflinePackageInstallationFixtures.apiPath())
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", OfflinePackageManifestFixtures.POLICE_PHONE_ID)
                .header("Idempotency-Key", "idem-package-no-auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OfflinePackageInstallationFixtures.readyReportJson()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")));
  }

  @Test
  @DisplayName("same Idempotency-Key replays response without duplicate event")
  void duplicate_idempotency_key_replays_without_duplicate_event() throws Exception {
    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc
          .perform(
              post(OfflinePackageInstallationFixtures.apiPath())
                  .header("Authorization", "Bearer app-package-session")
                  .header("X-Client-Channel", "APP")
                  .header("X-PolicePhone-Id", OfflinePackageManifestFixtures.POLICE_PHONE_ID)
                  .header("Idempotency-Key", "idem-package-duplicate-001")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(OfflinePackageInstallationFixtures.readyReportJson()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id", is(OfflinePackageInstallationFixtures.INSTALLATION_ID)))
          .andExpect(jsonPath("$.status", is("READY")));
    }

    assertThat(eventHub.findByType(OfflinePackageInstallationFixtures.EVENT_TYPE)).hasSize(1);
  }

  @TestConfiguration
  static class EventHubCaptureConfig {

    @Bean
    @Primary
    MockEventHub mockEventHub() {
      return new MockEventHub();
    }
  }
}
