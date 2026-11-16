package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.global.event.CapturingEventPublisher;
import com.surimap.incident.repository.IncidentReadMapper;
import com.surimap.incident.repository.IncidentReadRows.DetailRow;
import com.surimap.offlinepackage.fixture.OfflinePackageInstallationFixtures;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures;
import com.surimap.offlinepackage.query.OfflinePackageInstallationQuery;
import com.surimap.support.auth.WithMockAccount;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** L6-T06A RED tests for S7 offline_package_installation API and event publication. */
@SpringBootTest(properties = "tileserver.mode=fixture")
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("L6-T06A offline_package_installation API/event RED")
class OfflinePackageInstallationApiRedTest {

  // H2 테스트 seed에서 업무폰 dev-precinct-phone-01에 연결된 계정이다.
  private static final String PHONE_ACCOUNT_ID = "11111111-1111-1111-1111-111111110003";
  // H2 테스트 seed에서 업무폰 dev-precinct-car-01과 그 업무폰에 연결된 계정이다.
  private static final String SECOND_PHONE_ID = "50000000-0000-0000-0000-000000000001";
  private static final String SECOND_PHONE_ACCOUNT_ID = "11111111-1111-1111-1111-111111110002";

  @Autowired private MockMvc mockMvc;

  @Autowired private CapturingEventPublisher eventHub;

  @Autowired private OfflinePackageInstallationQuery installationQuery;

  @Autowired private JdbcTemplate jdbcTemplate;

  // H2 테스트 DB에는 incident 테이블이 없다. 그래서 작업자는 manifest 요청과 설치 보고의 사건 권한 확인에 쓰이는 사건 조회만 대역으로 바꿨다.
  // 사건 권한 확인은 OfflinePackageManifestApiTest와 OfflinePackageInstallationApiTest에서 테스트용 PostGIS로 검증된다.
  @MockitoBean private IncidentReadMapper incidentReadMapper;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void resetEventHub() {
    eventHub.reset();
    when(incidentReadMapper.findActiveDetailByIncidentIdAndAccountId(
            UUID.fromString(OfflinePackageManifestFixtures.INCIDENT_ID),
            UUID.fromString(PHONE_ACCOUNT_ID)))
        .thenReturn(Optional.of(new DetailRow()));
    when(incidentReadMapper.findActiveDetailByIncidentIdAndAccountId(
            UUID.fromString(OfflinePackageManifestFixtures.INCIDENT_ID),
            UUID.fromString(SECOND_PHONE_ACCOUNT_ID)))
        .thenReturn(Optional.of(new DetailRow()));
    // 설치 보고에는 등록된 업무폰이 필요하다. 공유 H2 DB에서는 업무폰 H2 테스트가 끝난 뒤 업무폰 등록이 해제된 채로 남는다.
    // 그래서 작업자는 이 테스트에서 쓰는 두 업무폰을 테스트마다 등록 상태로 맞추게 했다.
    jdbcTemplate.update(
        "UPDATE police_phone SET registered = TRUE WHERE id IN (?, ?)",
        UUID.fromString(OfflinePackageManifestFixtures.POLICE_PHONE_ID),
        UUID.fromString(SECOND_PHONE_ID));
  }

  @Test
  @WithMockAccount(
      accountId = PHONE_ACCOUNT_ID,
      policePhoneId = OfflinePackageManifestFixtures.POLICE_PHONE_ID)
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
  @WithMockAccount(
      accountId = PHONE_ACCOUNT_ID,
      policePhoneId = OfflinePackageManifestFixtures.POLICE_PHONE_ID)
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
  @WithMockAccount(
      accountId = PHONE_ACCOUNT_ID,
      policePhoneId = OfflinePackageManifestFixtures.POLICE_PHONE_ID)
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
              assertThat(event.getType()).isEqualTo(OfflinePackageInstallationFixtures.EVENT_TYPE);
              assertThat(event.getPayloadFormatVersion()).isEqualTo(1);
              assertThat(event.getSourceEntityType())
                  .isEqualTo(OfflinePackageInstallationFixtures.SOURCE_ENTITY_TYPE);
              assertThat(event.getPayload())
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
  @WithMockAccount(
      accountId = PHONE_ACCOUNT_ID,
      policePhoneId = OfflinePackageManifestFixtures.POLICE_PHONE_ID)
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

    // 본문의 업무폰은 등록된 활성 업무폰이어야 한다. 그래서 작업자는 두 번째 업무폰을 H2 테스트 seed에 등록된 업무폰으로 바꿨다.
    // 두 번째 보고의 토큰 계정은 그 업무폰에 연결된 계정이다.
    String secondPhoneReadyReport =
        OfflinePackageInstallationFixtures.readyReportJson()
            .replace(OfflinePackageManifestFixtures.POLICE_PHONE_ID, SECOND_PHONE_ID)
            .replace(
                "\"sequence\": %d".formatted(OfflinePackageInstallationFixtures.SEQUENCE),
                "\"sequence\": %d".formatted(OfflinePackageInstallationFixtures.SEQUENCE + 1));
    TestSecurityContextHolder.setAuthentication(
        new SuriMapAuthentication(
            SECOND_PHONE_ACCOUNT_ID,
            AccountType.PATROL_CAR,
            OrganizationType.POLICE_SUBSTATION,
            Channel.APP,
            SECOND_PHONE_ID,
            List.of()));

    mockMvc
        .perform(
            post(OfflinePackageInstallationFixtures.apiPath())
                .header("Authorization", "Bearer app-package-session")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", SECOND_PHONE_ID)
                .header("Idempotency-Key", "idem-package-phone-02")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondPhoneReadyReport))
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
              assertThat(row.policePhoneId()).isEqualTo(SECOND_PHONE_ID);
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
  @WithMockAccount(
      accountId = PHONE_ACCOUNT_ID,
      policePhoneId = OfflinePackageManifestFixtures.POLICE_PHONE_ID)
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
    CapturingEventPublisher mockEventHub() {
      return new CapturingEventPublisher();
    }
  }
}
