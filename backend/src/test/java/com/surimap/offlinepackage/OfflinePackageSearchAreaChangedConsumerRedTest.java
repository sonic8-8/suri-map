package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.surimap.board.BoardAssembler;
import com.surimap.board.BoardAssemblyRequest;
import com.surimap.board.BoardDTO;
import com.surimap.board.BoardSlotRow;
import com.surimap.board.BoardSourceRow;
import com.surimap.board.PackageBadgeBoardAssembler;
import com.surimap.eventhub.adapter.MockEventHub;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.offlinepackage.consumer.SearchAreaChangedConsumer;
import com.surimap.offlinepackage.dto.OfflinePackageInstallationReportRequest;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse;
import com.surimap.offlinepackage.exception.OfflinePackageApiException;
import com.surimap.offlinepackage.fixture.OfflinePackageInstallationFixtures;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures;
import com.surimap.offlinepackage.query.OfflinePackageInstallationQuery;
import com.surimap.offlinepackage.query.OfflinePackageInstallationStatus;
import com.surimap.offlinepackage.service.OfflinePackageService;
import com.surimap.policephone.PolicePhoneDbFixtureSupport;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** L6-T07 RED: S7 consumes S2 SEARCH_AREA_CHANGED and marks old packages STALE. */
@SpringBootTest(properties = "tileserver.mode=fixture")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("L6-T07 SEARCH_AREA_CHANGED stale offline package consumer RED")
class OfflinePackageSearchAreaChangedConsumerRedTest {

  @Autowired private OfflinePackageInstallationQuery installationQuery;

  @Autowired private OfflinePackageService offlinePackageService;

  @Autowired private SearchAreaChangedConsumer searchAreaChangedConsumer;

  @Autowired private MockEventHub eventHub;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void seedPackageFixture() {
    jdbcTemplate.update("DELETE FROM offline_package_installation");
    jdbcTemplate.update("DELETE FROM offline_package_manifest");
    PolicePhoneDbFixtureSupport.ensureGuardFixtures(jdbcTemplate);
    ensurePackagePolicePhones();
    eventHub.reset();
    offlinePackageService.manifest(
        OfflinePackageManifestFixtures.INCIDENT_ID, OfflinePackageManifestFixtures.POLICE_PHONE_ID);
  }

  private void ensurePackagePolicePhones() {
    ensurePackagePolicePhone(
        OfflinePackageInstallationFixtures.SEEDED_PHONE_02_ID,
        "dev-precinct-phone-02",
        "경찰서 팀폰 02");
    ensurePackagePolicePhone(
        OfflinePackageInstallationFixtures.SEEDED_PHONE_05_ID,
        "dev-precinct-phone-05",
        "경찰서 팀폰 05");
  }

  private void ensurePackagePolicePhone(String id, String phoneCode, String displayName) {
    jdbcTemplate.update(
        """
        MERGE INTO police_phone (
            id,
            phone_code,
            display_name,
            account_id,
            status,
            registered,
            last_heartbeat_at,
            last_sync_at,
            heartbeat_sequence,
            last_heartbeat_event_id,
            version
        ) KEY(id) VALUES (?, ?, ?, ?, 'ACTIVE', TRUE, NULL, NULL, 0, NULL, 1)
        """,
        UUID.fromString(id),
        phoneCode,
        displayName,
        UUID.fromString(OfflinePackageManifestFixtures.ACCOUNT_ID));
  }

  @Test
  @DisplayName("overall area change increments manifest and exposes re-download required")
  void search_area_changed_marks_ready_and_partial_installations_stale_for_package_badge() {
    assertThat(row(OfflinePackageInstallationFixtures.SEEDED_READY_INSTALLATION_ID).status())
        .isEqualTo("READY");
    assertThat(row(OfflinePackageInstallationFixtures.SEEDED_PARTIAL_INSTALLATION_ID).status())
        .isEqualTo("PARTIAL");
    assertThat(row(OfflinePackageInstallationFixtures.SEEDED_DOWNLOADING_INSTALLATION_ID).status())
        .isEqualTo("DOWNLOADING");
    OffsetDateTime beforeRevision = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1);

    invokeSearchAreaChangedConsumer(searchAreaChangedEvent());

    OfflinePackageInstallationStatus readyPhone =
        row(OfflinePackageInstallationFixtures.SEEDED_READY_INSTALLATION_ID);
    OfflinePackageInstallationStatus partialPhone =
        row(OfflinePackageInstallationFixtures.SEEDED_PARTIAL_INSTALLATION_ID);
    OfflinePackageInstallationStatus downloadingPhone =
        row(OfflinePackageInstallationFixtures.SEEDED_DOWNLOADING_INSTALLATION_ID);
    assertStaleForCurrentManifest(readyPhone);
    assertStaleForCurrentManifest(partialPhone);
    assertStaleForCurrentManifest(downloadingPhone);
    assertCurrentManifestRevisionRequiresDownload();
    assertCurrentManifestRevisionExpiresAfter(beforeRevision);

    assertThat(eventHub.findByType(OfflinePackageInstallationFixtures.EVENT_TYPE))
        .extracting(event -> event.payload().get("id"), event -> event.payload().get("status"))
        .contains(
            tuple(OfflinePackageInstallationFixtures.SEEDED_READY_INSTALLATION_ID, "STALE"),
            tuple(OfflinePackageInstallationFixtures.SEEDED_PARTIAL_INSTALLATION_ID, "STALE"),
            tuple(OfflinePackageInstallationFixtures.SEEDED_DOWNLOADING_INSTALLATION_ID, "STALE"));

    BoardDTO board = boardFromPackageBadgeRows();
    assertPackageBadgeRequiresRedownload(
        board, OfflinePackageInstallationFixtures.SEEDED_READY_INSTALLATION_ID);
    assertPackageBadgeRequiresRedownload(
        board, OfflinePackageInstallationFixtures.SEEDED_PARTIAL_INSTALLATION_ID);
    assertPackageBadgeRequiresRedownload(
        board, OfflinePackageInstallationFixtures.SEEDED_DOWNLOADING_INSTALLATION_ID);

    int publishedStatusChanges =
        eventHub.findByType(OfflinePackageInstallationFixtures.EVENT_TYPE).size();
    invokeSearchAreaChangedConsumer(searchAreaChangedEvent());
    assertThat(eventHub.findByType(OfflinePackageInstallationFixtures.EVENT_TYPE))
        .hasSize(publishedStatusChanges);
    assertThat(
            offlinePackageService
                .manifest(
                    OfflinePackageManifestFixtures.INCIDENT_ID,
                    OfflinePackageManifestFixtures.POLICE_PHONE_ID)
                .manifestVersion())
        .isEqualTo(OfflinePackageInstallationFixtures.STALE_MANIFEST_VERSION);
  }

  @Test
  @DisplayName("stale transition publish request uses a new event id for the changed version")
  void stale_transition_publish_request_uses_new_event_id_for_changed_version() {
    offlinePackageService.reportInstallation(
        OfflinePackageManifestFixtures.INCIDENT_ID,
        "idem-package-ready-before-stale",
        readyReport());

    invokeSearchAreaChangedConsumer(searchAreaChangedEvent());

    assertThat(eventHub.findByType(OfflinePackageInstallationFixtures.EVENT_TYPE))
        .filteredOn(
            event ->
                OfflinePackageInstallationFixtures.INSTALLATION_ID.equals(
                    event.payload().get("id")))
        .extracting(MockEventHub.CapturedPublish::eventId)
        .doesNotHaveDuplicates()
        .hasSize(2);
  }

  @Test
  @DisplayName("old manifest READY report is rejected after overall area revision")
  void old_manifest_ready_report_is_rejected_after_overall_area_revision() {
    invokeSearchAreaChangedConsumer(searchAreaChangedEvent());

    assertThatThrownBy(
            () ->
                offlinePackageService.reportInstallation(
                    OfflinePackageManifestFixtures.INCIDENT_ID,
                    "idem-package-old-manifest-ready-after-stale",
                    readyReport()))
        .isInstanceOf(OfflinePackageApiException.class)
        .hasMessageContaining("write_conflict");

    OfflinePackageInstallationStatus readyPhone =
        row(OfflinePackageInstallationFixtures.SEEDED_READY_INSTALLATION_ID);
    assertThat(readyPhone.status()).isEqualTo("STALE");
    assertThat(readyPhone.manifestVersion())
        .isEqualTo(OfflinePackageManifestFixtures.MANIFEST_VERSION);
    assertThat(readyPhone.activeManifestVersion())
        .isEqualTo(OfflinePackageInstallationFixtures.STALE_MANIFEST_VERSION);
  }

  private void invokeSearchAreaChangedConsumer(PublishRequest event) {
    searchAreaChangedConsumer.consume(event);
  }

  private OfflinePackageInstallationStatus row(String id) {
    return installationQuery.byIncident(OfflinePackageManifestFixtures.INCIDENT_ID).stream()
        .filter(status -> id.equals(status.id()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing package installation row: " + id));
  }

  private static void assertStaleForCurrentManifest(OfflinePackageInstallationStatus status) {
    assertThat(status.status()).isEqualTo("STALE");
    assertThat(status.readyForOfflineUse()).isFalse();
    assertThat(status.manifestVersion()).isEqualTo(OfflinePackageManifestFixtures.MANIFEST_VERSION);
    assertThat(status.activeManifestVersion())
        .isEqualTo(OfflinePackageInstallationFixtures.STALE_MANIFEST_VERSION);
  }

  private void assertCurrentManifestRevisionRequiresDownload() {
    OfflinePackageManifestResponse manifest =
        offlinePackageService.manifest(
            OfflinePackageManifestFixtures.INCIDENT_ID,
            OfflinePackageManifestFixtures.POLICE_PHONE_ID);
    assertThat(manifest.manifestVersion())
        .isEqualTo(OfflinePackageInstallationFixtures.STALE_MANIFEST_VERSION);
    assertThat(manifest.manifestId())
        .isEqualTo(OfflinePackageInstallationFixtures.STALE_MANIFEST_ID);
    assertThat(manifest.packageItems())
        .extracting(OfflinePackageManifestResponse.PackageItem::status)
        .containsOnly("PENDING");
  }

  private void assertCurrentManifestRevisionExpiresAfter(OffsetDateTime beforeRevision) {
    OffsetDateTime expiresAt =
        jdbcTemplate.queryForObject(
            """
            SELECT expires_at
            FROM offline_package_manifest
            WHERE id = ?::uuid
            """,
            OffsetDateTime.class,
            OfflinePackageInstallationFixtures.STALE_MANIFEST_ID);

    assertThat(expiresAt).isAfter(beforeRevision);
  }

  private BoardDTO boardFromPackageBadgeRows() {
    List<BoardSourceRow> sourceRows =
        new PackageBadgeBoardAssembler(installationQuery)
            .sourceRowsByIncident(OfflinePackageManifestFixtures.INCIDENT_ID);
    return new BoardAssembler()
        .assemble(
            new BoardAssemblyRequest(
                OfflinePackageManifestFixtures.INCIDENT_ID,
                "board-response-package-badge-l6-t07",
                OfflinePackageInstallationFixtures.STALE_MANIFEST_VERSION,
                OfflinePackageInstallationFixtures.SERVER_TS,
                OfflinePackageManifestFixtures.OP_ID,
                List.of(OfflinePackageManifestFixtures.OP_ID),
                "overall-area-hash-precinct-revised",
                sourceRows));
  }

  @SuppressWarnings("unchecked")
  private static void assertPackageBadgeRequiresRedownload(BoardDTO board, String id) {
    BoardSlotRow row = board.slotRow("package_badge", id);
    assertThat(row.status()).isEqualTo("STALE");
    assertThat(row.version()).isPositive();
    assertThat(row.payload())
        .containsEntry("packageStatus", "STALE")
        .containsEntry("policePhoneCode", statusPhoneCode(id))
        .containsEntry("policePhoneName", statusPhoneName(id))
        .containsEntry("readyForOfflineUse", false)
        .containsEntry("manifestVersion", OfflinePackageManifestFixtures.MANIFEST_VERSION);

    Map<String, Object> warningInput = (Map<String, Object>) row.payload().get("localWarningInput");
    assertThat(warningInput)
        .containsEntry("packageStatus", "STALE")
        .containsEntry("raised", true)
        .containsEntry("manifestVersion", OfflinePackageManifestFixtures.MANIFEST_VERSION)
        .containsEntry(
            "activeManifestVersion", OfflinePackageInstallationFixtures.STALE_MANIFEST_VERSION);
  }

  private static String statusPhoneCode(String id) {
    if (OfflinePackageInstallationFixtures.SEEDED_READY_INSTALLATION_ID.equals(id)) {
      return OfflinePackageManifestFixtures.POLICE_PHONE_CODE;
    }
    if (OfflinePackageInstallationFixtures.SEEDED_PARTIAL_INSTALLATION_ID.equals(id)) {
      return "dev-precinct-phone-02";
    }
    if (OfflinePackageInstallationFixtures.SEEDED_DOWNLOADING_INSTALLATION_ID.equals(id)) {
      return "dev-precinct-phone-05";
    }
    return id;
  }

  private static String statusPhoneName(String id) {
    if (OfflinePackageInstallationFixtures.SEEDED_READY_INSTALLATION_ID.equals(id)) {
      return "종로 지구대 팀 폴리폰";
    }
    if (OfflinePackageInstallationFixtures.SEEDED_PARTIAL_INSTALLATION_ID.equals(id)) {
      return "경찰서 팀폰 02";
    }
    if (OfflinePackageInstallationFixtures.SEEDED_DOWNLOADING_INSTALLATION_ID.equals(id)) {
      return "경찰서 팀폰 05";
    }
    return statusPhoneCode(id);
  }

  private static PublishRequest searchAreaChangedEvent() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", BoundaryAreaFixtures.OVERALL_AREA_ID.toString());
    payload.put("incidentId", OfflinePackageManifestFixtures.INCIDENT_ID);
    payload.put("status", "ACTIVE");
    payload.put("version", BoundaryAreaFixtures.OVERALL_AREA_VERSION);
    payload.put("sequence", BoundaryAreaFixtures.OVERALL_AREA_EVENT_SEQUENCE);
    payload.put("geometry", "overall-area-hash-precinct-revised");
    payload.put("serverTs", OfflinePackageInstallationFixtures.SERVER_TS.toString());
    return new PublishRequest(
        stableUuid("event:" + BoundaryAreaFixtures.OVERALL_AREA_EVENT_ID),
        stableUuid("incident:" + OfflinePackageManifestFixtures.INCIDENT_ID),
        OfflinePackageInstallationFixtures.SEARCH_AREA_CHANGED_EVENT_TYPE,
        1,
        "search_area",
        stableUuid("search_area:" + BoundaryAreaFixtures.OVERALL_AREA_ID),
        OfflinePackageInstallationFixtures.SERVER_TS.toInstant(),
        payload);
  }

  private static OfflinePackageInstallationReportRequest readyReport() {
    return new OfflinePackageInstallationReportRequest(
        OfflinePackageManifestFixtures.POLICE_PHONE_ID,
        OfflinePackageManifestFixtures.MANIFEST_ID,
        OfflinePackageManifestFixtures.MANIFEST_VERSION,
        "READY",
        7,
        7,
        0,
        OfflinePackageInstallationFixtures.VERSION,
        OfflinePackageInstallationFixtures.CLIENT_TS,
        true,
        List.of(),
        null,
        OfflinePackageInstallationFixtures.SEQUENCE,
        0L);
  }

  private static UUID stableUuid(String source) {
    return UUID.nameUUIDFromBytes(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
