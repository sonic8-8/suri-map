package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.global.event.CapturingEventPublisher;
import com.surimap.global.event.EventPublishRequest;
import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.offlinepackage.consumer.SearchAreaChangedConsumer;
import com.surimap.offlinepackage.dto.OfflinePackageInstallationReportRequest;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse;
import com.surimap.offlinepackage.exception.OfflinePackageApiException;
import com.surimap.offlinepackage.fixture.OfflinePackageInstallationFixtures;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures;
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

  @Autowired private OfflinePackageService offlinePackageService;

  @Autowired private SearchAreaChangedConsumer searchAreaChangedConsumer;

  @Autowired private CapturingEventPublisher eventHub;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void seedPackageFixture() {
    jdbcTemplate.update("DELETE FROM offline_package_installation");
    jdbcTemplate.update("DELETE FROM offline_package_manifest");
    PolicePhoneDbFixtureSupport.ensureGuardFixtures(jdbcTemplate);
    eventHub.reset();
    offlinePackageService.manifest(
        OfflinePackageManifestFixtures.INCIDENT_ID,
        OfflinePackageManifestFixtures.POLICE_PHONE_ID,
        UUID.fromString(OfflinePackageManifestFixtures.ACCOUNT_ID));
  }

  @Test
  @DisplayName("전체 구역이 바뀌면 manifest 버전이 오르고 READY, PARTIAL, DOWNLOADING 설치 상태가 STALE로 바뀐다")
  void search_area_changed_marks_ready_partial_and_downloading_installations_stale() {
    // given: 이전 manifest에 READY, PARTIAL, DOWNLOADING 설치 상태가 있다.
    assertThat(installationRow(OfflinePackageInstallationFixtures.SEEDED_READY_INSTALLATION_ID))
        .containsEntry("status", "READY");
    assertThat(installationRow(OfflinePackageInstallationFixtures.SEEDED_PARTIAL_INSTALLATION_ID))
        .containsEntry("status", "PARTIAL");
    assertThat(
            installationRow(OfflinePackageInstallationFixtures.SEEDED_DOWNLOADING_INSTALLATION_ID))
        .containsEntry("status", "DOWNLOADING");
    OffsetDateTime beforeRevision = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1);

    // when: 전체 구역 변경 이벤트를 소비한다.
    invokeSearchAreaChangedConsumer(searchAreaChangedEvent());

    // then: 세 설치 상태가 이전 manifest 버전에 남은 채 STALE로 바뀐다. 현재 manifest는 새 버전이다.
    assertStaleForPreviousManifest(OfflinePackageInstallationFixtures.SEEDED_READY_INSTALLATION_ID);
    assertStaleForPreviousManifest(
        OfflinePackageInstallationFixtures.SEEDED_PARTIAL_INSTALLATION_ID);
    assertStaleForPreviousManifest(
        OfflinePackageInstallationFixtures.SEEDED_DOWNLOADING_INSTALLATION_ID);
    assertCurrentManifestRevisionRequiresDownload();
    assertCurrentManifestRevisionExpiresAfter(beforeRevision);

    // then: 같은 이벤트를 다시 소비해도 manifest 버전은 그대로다.
    invokeSearchAreaChangedConsumer(searchAreaChangedEvent());
    assertThat(
            offlinePackageService
                .manifest(
                    OfflinePackageManifestFixtures.INCIDENT_ID,
                    OfflinePackageManifestFixtures.POLICE_PHONE_ID,
                    UUID.fromString(OfflinePackageManifestFixtures.ACCOUNT_ID))
                .manifestVersion())
        .isEqualTo(OfflinePackageInstallationFixtures.STALE_MANIFEST_VERSION);
  }

  @Test
  @DisplayName("설치 보고를 저장하고 STALE로 바꿔도 OFFLINE_PACKAGE_INSTALLATION_CHANGED는 발행되지 않는다")
  void report_and_stale_transition_do_not_publish_installation_changed() {
    // given: 업무폰이 현재 manifest의 READY를 보고했다.
    offlinePackageService.reportInstallation(
        OfflinePackageManifestFixtures.INCIDENT_ID,
        "idem-package-ready-before-stale",
        readyReport(),
        true);

    // when: 전체 구역 변경 이벤트를 소비한다.
    invokeSearchAreaChangedConsumer(searchAreaChangedEvent());

    // then: 보고한 설치 상태는 STALE로 바뀌고, 설치 변경 이벤트는 한 번도 발행되지 않는다.
    assertThat(installationRow(OfflinePackageInstallationFixtures.INSTALLATION_ID))
        .containsEntry("status", "STALE");
    assertThat(eventHub.findByType(OfflinePackageInstallationFixtures.EVENT_TYPE)).isEmpty();
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
                    readyReport(),
                    true))
        .isInstanceOf(OfflinePackageApiException.class)
        .hasMessageContaining("write_conflict");

    assertStaleForPreviousManifest(OfflinePackageInstallationFixtures.SEEDED_READY_INSTALLATION_ID);
  }

  private void invokeSearchAreaChangedConsumer(EventPublishRequest event) {
    searchAreaChangedConsumer.consume(event);
  }

  private Map<String, Object> installationRow(String id) {
    return jdbcTemplate.queryForMap(
        """
        SELECT i.status, m.manifest_version
        FROM offline_package_installation i
        JOIN offline_package_manifest m ON m.id = i.offline_package_manifest_id
        WHERE i.id = ?
        """,
        UUID.fromString(id));
  }

  private void assertStaleForPreviousManifest(String id) {
    Map<String, Object> row = installationRow(id);
    assertThat(row).containsEntry("status", "STALE");
    assertThat(((Number) row.get("manifest_version")).intValue())
        .isEqualTo(OfflinePackageManifestFixtures.MANIFEST_VERSION);
  }

  private void assertCurrentManifestRevisionRequiresDownload() {
    OfflinePackageManifestResponse manifest =
        offlinePackageService.manifest(
            OfflinePackageManifestFixtures.INCIDENT_ID,
            OfflinePackageManifestFixtures.POLICE_PHONE_ID,
            UUID.fromString(OfflinePackageManifestFixtures.ACCOUNT_ID));
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

  private static EventPublishRequest searchAreaChangedEvent() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", BoundaryAreaFixtures.OVERALL_AREA_ID.toString());
    payload.put("incidentId", OfflinePackageManifestFixtures.INCIDENT_ID);
    payload.put("status", "ACTIVE");
    payload.put("version", BoundaryAreaFixtures.OVERALL_AREA_VERSION);
    payload.put("sequence", BoundaryAreaFixtures.OVERALL_AREA_EVENT_SEQUENCE);
    payload.put("geometry", "overall-area-hash-precinct-revised");
    payload.put("serverTs", OfflinePackageInstallationFixtures.SERVER_TS.toString());
    return EventPublishRequest.builder()
        .eventId(stableUuid("event:" + BoundaryAreaFixtures.OVERALL_AREA_EVENT_ID))
        .incidentId(stableUuid("incident:" + OfflinePackageManifestFixtures.INCIDENT_ID))
        .type(OfflinePackageInstallationFixtures.SEARCH_AREA_CHANGED_EVENT_TYPE)
        .payloadFormatVersion(1)
        .sourceEntityType("search_area")
        .sourceEntityId(stableUuid("search_area:" + BoundaryAreaFixtures.OVERALL_AREA_ID))
        .occurredAt(OfflinePackageInstallationFixtures.SERVER_TS.toInstant())
        .payload(payload)
        .build();
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
    CapturingEventPublisher mockEventHub() {
      return new CapturingEventPublisher();
    }
  }
}
