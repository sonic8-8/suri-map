package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.offlinepackage.dto.OfflinePackageInstallationReportRequest;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse;
import com.surimap.offlinepackage.exception.OfflinePackageApiException;
import com.surimap.offlinepackage.service.OfflinePackageService;
import com.surimap.retention.purge.PurgeHook;
import com.surimap.retention.purge.PurgeHookName;
import com.surimap.retention.purge.PurgeHookRequest;
import com.surimap.retention.purge.PurgeHookResult;
import com.surimap.retention.purge.PurgeHookStatus;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** L6-T09A RED tests for S7 offline package purge hook and tombstone transition. */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("L6-T09A offline package purge hook RED")
class OfflinePackagePurgeHookRedTest {

  private static final UUID INCIDENT_ID =
      UUID.fromString("77777777-0000-4000-8000-0000000009a1");
  private static final UUID PURGE_RUN_ID =
      UUID.fromString("77777777-0000-4000-8000-0000000009a2");
  private static final Instant CLOSED_AT = Instant.parse("2026-05-07T00:10:00Z");
  private static final Instant PURGE_DEADLINE_TS = Instant.parse("2026-05-08T00:10:00Z");
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-07T09:10:00+09:00");
  private static final String MANIFEST_ID = "purge-manifest-l6-t09a";
  private static final List<String> PRE_PURGE_STATUSES =
      List.of("NOT_STARTED", "DOWNLOADING", "PARTIAL", "READY", "STALE", "FAILED");

  @Autowired private ApplicationContext applicationContext;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private OfflinePackageService offlinePackageService;

  @BeforeEach
  void resetPackageTables() {
    jdbcTemplate.update("DELETE FROM offline_package_installation");
    jdbcTemplate.update("DELETE FROM offline_package_manifest");
  }

  @Test
  @DisplayName("S7 exposes PackagePurgeHook through the S1-3 PurgeHook boundary")
  void exposesPackagePurgeHookThroughCommonPurgeHookBoundary() throws Exception {
    Class<?> hookType = packagePurgeHookType();

    assertThat(hookType.isInterface()).isTrue();
    Method purgeMethod =
        hookType.getMethod(
            "purgeIncidentPackage", UUID.class, UUID.class, Instant.class, Instant.class);
    assertThat(purgeMethod.getReturnType()).isEqualTo(PurgeHookResult.class);

    assertThat(applicationContext.getBeansOfType(hookType))
        .as("S7 must provide a PackagePurgeHook bean for S1-3 to consume")
        .isNotEmpty();

    PurgeHook commonHook = packageHookFromS13Boundary();
    assertThat(commonHook.name()).isEqualTo(PurgeHookName.OFFLINE_PACKAGE);
  }

  @Test
  @DisplayName("PackagePurgeHook transitions every incident installation row to PURGED tombstone")
  void purgeHookTransitionsInstallationsToPurgedTombstoneAndReturnsSucceededCounts() {
    seedPackageRows();
    long manifestCount = count("offline_package_manifest");
    long installationCount = count("offline_package_installation");

    PurgeHookResult result = packageHookFromS13Boundary().purge(purgeRequest());

    assertThat(result.status()).isEqualTo(PurgeHookStatus.SUCCEEDED);
    assertThat(result.purgedCount()).isEqualTo(manifestCount + installationCount);
    assertThat(result.retainedCount()).isZero();
    assertThat(result.errorCode()).isNull();

    assertThat(installationRows())
        .hasSize(PRE_PURGE_STATUSES.size())
        .allSatisfy(
            row -> {
              assertThat(row).containsEntry("STATUS", "PURGED");
              assertThat(row).containsEntry("TOTAL_ITEM_COUNT", 0);
              assertThat(row).containsEntry("COMPLETED_ITEM_COUNT", 0);
              assertThat(row).containsEntry("FAILED_ITEM_COUNT", 0);
              assertThat(row.get("FAILED_ITEM_KEYS")).isNull();
              assertThat(row.get("LAST_ERROR_CODE")).isNull();
              assertThat(row.get("LAST_REPORTED_BY_ACCOUNT_ID")).isNull();
            });
  }

  @Test
  @DisplayName("purged package manifest no longer exposes missing_person package data")
  void purgeHookBlocksOrSanitizesManifestMissingPersonPayload() {
    seedPackageRows();

    packageHookFromS13Boundary().purge(purgeRequest());

    List<String> remainingPayloads =
        jdbcTemplate.queryForList(
            "SELECT manifest_payload FROM offline_package_manifest WHERE incident_id = ?",
            String.class,
            INCIDENT_ID.toString());
    assertThat(remainingPayloads)
        .allSatisfy(
            payload ->
                assertThat(payload)
                    .doesNotContain(
                        "missing_person",
                        "missingPerson",
                        "MISSING_PERSON_CACHE",
                        "가상 실종자",
                        "lastSeenLocationText",
                        "photoObjectKey"));

    assertManifestReadBlockedOrSanitized();
  }

  @Test
  @DisplayName("purged manifest rejects later package installation reports without resurrecting state")
  void purgeHookPreventsPostPurgeInstallationWrites() {
    seedPackageRows();

    packageHookFromS13Boundary().purge(purgeRequest());

    assertThatThrownBy(
            () ->
                offlinePackageService.reportInstallation(
                    INCIDENT_ID.toString(),
                    "idem-purged-package-ready",
                    readyReport()))
        .isInstanceOf(OfflinePackageApiException.class)
        .hasMessageContaining("incident_closed");

    assertThat(installationRows())
        .hasSize(PRE_PURGE_STATUSES.size())
        .allSatisfy(row -> assertThat(row).containsEntry("STATUS", "PURGED"));
  }

  @Test
  @DisplayName("manifest-only package purge counts and blocks sanitized manifest reads")
  void purgeHookSanitizesManifestEvenWhenNoInstallationRowsExist() {
    seedManifest();

    PurgeHookResult result = packageHookFromS13Boundary().purge(purgeRequest());

    assertThat(result.status()).isEqualTo(PurgeHookStatus.SUCCEEDED);
    assertThat(result.purgedCount()).isEqualTo(1);
    assertThat(result.retainedCount()).isZero();
    assertThat(remainingManifestPayloads())
        .singleElement()
        .satisfies(
            payload ->
                assertThat(payload)
                    .doesNotContain("missing_person", "MISSING_PERSON_CACHE", "가상 실종자"));
    assertManifestReadBlockedOrSanitized();
  }

  @Test
  @DisplayName("post-purge SEARCH_AREA_CHANGED does not create a new manifest with missing_person data")
  void purgeHookPreventsPostPurgeManifestRevisionFromSearchAreaChanged() {
    seedPackageRows();

    packageHookFromS13Boundary().purge(purgeRequest());
    offlinePackageService.consumeSearchAreaChanged(searchAreaChangedEvent());

    assertThat(remainingManifestPayloads())
        .hasSize(1)
        .allSatisfy(
            payload ->
                assertThat(payload)
                    .doesNotContain("missing_person", "MISSING_PERSON_CACHE", "가상 실종자"));
    assertManifestReadBlockedOrSanitized();
  }

  @Test
  @DisplayName("PackagePurgeHook is idempotent for repeated S1-3 calls")
  void purgeHookIsIdempotentOnRepeatedCalls() {
    seedPackageRows();
    PurgeHook hook = packageHookFromS13Boundary();

    PurgeHookResult first = hook.purge(purgeRequest());
    List<Map<String, Object>> rowsAfterFirst = installationRows();
    List<String> payloadsAfterFirst = remainingManifestPayloads();

    PurgeHookResult second = hook.purge(purgeRequest());

    assertThat(second.status()).isEqualTo(PurgeHookStatus.SUCCEEDED);
    assertThat(second.retainedCount()).isZero();
    assertThat(second.errorCode()).isNull();
    assertThat(second.purgedCount()).isBetween(0L, first.purgedCount());
    assertThat(installationRows()).isEqualTo(rowsAfterFirst);
    assertThat(remainingManifestPayloads()).isEqualTo(payloadsAfterFirst);
  }

  private void assertManifestReadBlockedOrSanitized() {
    try {
      OfflinePackageManifestResponse manifest =
          offlinePackageService.manifest(INCIDENT_ID.toString(), "dev-purge-phone-01");
      assertThat(manifest.missingPerson()).isNull();
      assertThat(manifest.packageItems())
          .noneSatisfy(
              item -> {
                assertThat(item.itemType()).isEqualTo("MISSING_PERSON_CACHE");
                assertThat(item.itemKey()).containsIgnoringCase("missing");
              });
    } catch (RuntimeException exception) {
      assertThat(exception.getMessage())
          .containsAnyOf(
              "offline package manifest not found",
              "incident_closed",
              "package_purged",
              "package_unavailable");
    }
  }

  private PurgeHook packageHookFromS13Boundary() {
    List<PurgeHook> hooks =
        applicationContext.getBeanProvider(PurgeHook.class).orderedStream()
            .filter(hook -> hook.name() == PurgeHookName.OFFLINE_PACKAGE)
            .toList();
    assertThat(hooks)
        .as("S7 must register exactly one OFFLINE_PACKAGE PurgeHook for S1-3")
        .hasSize(1);
    return hooks.get(0);
  }

  private static Class<?> packagePurgeHookType() {
    for (String className :
        List.of(
            "com.surimap.offlinepackage.purge.PackagePurgeHook",
            "com.surimap.offlinepackage.PackagePurgeHook")) {
      try {
        return Class.forName(className);
      } catch (ClassNotFoundException ignored) {
        // Try the next local S7 package convention.
      }
    }
    fail(
        "Expected S7 PackagePurgeHook contract type in com.surimap.offlinepackage(.purge)");
    throw new IllegalStateException("unreachable");
  }

  private static PurgeHookRequest purgeRequest() {
    return new PurgeHookRequest(INCIDENT_ID, PURGE_RUN_ID, CLOSED_AT, PURGE_DEADLINE_TS);
  }

  private void seedPackageRows() {
    seedManifest();
    int index = 0;
    for (String status : PRE_PURGE_STATUSES) {
      seedInstallation(status, ++index);
    }
  }

  private void seedManifest() {
    jdbcTemplate.update(
        """
        INSERT INTO offline_package_manifest (
            id,
            incident_id,
            manifest_version,
            operational_period_id,
            overall_search_area_id,
            overall_search_area_version,
            manifest_hash,
            manifest_format_version,
            manifest_payload,
            expires_at,
            created_at,
            updated_at
        )
        VALUES (?, ?, 1, 'op-purge-l6-t09a', 'osa-purge-l6-t09a', 1,
                'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 1,
                ?, ?, ?, ?)
        """,
        MANIFEST_ID,
        INCIDENT_ID.toString(),
        manifestPayloadWithMissingPerson(),
        NOW.plusHours(24),
        NOW,
        NOW);
  }

  private void seedInstallation(String status, int index) {
    jdbcTemplate.update(
        """
        INSERT INTO offline_package_installation (
            id,
            offline_package_manifest_id,
            police_phone_id,
            last_reported_by_account_id,
            status,
            total_item_count,
            completed_item_count,
            failed_item_count,
            failed_item_keys,
            last_error_code,
            last_reported_at,
            version,
            created_at,
            updated_at
        )
        VALUES (?, ?, ?, '11111111-1111-1111-1111-111111119903', ?, 7, 3, 2, NULL, 'tile-timeout', ?, 1, ?, ?)
        """,
        "pkg-purge-l6-t09a-%02d".formatted(index),
        MANIFEST_ID,
        "dev-purge-phone-%02d".formatted(index),
        status,
        NOW,
        NOW,
        NOW);
  }

  private static String manifestPayloadWithMissingPerson() {
    return """
        {
          "incident": {"id": "%s", "status": "OPEN"},
          "missing_person": {
            "displayName": "가상 실종자",
            "photoObjectKey": "photo/missing-person/purge-target.jpg",
            "lastSeenLocationText": "인왕산 북측 산책로"
          },
          "packageItems": [
            {"itemKey": "missing-person:%s", "itemType": "MISSING_PERSON_CACHE"}
          ]
        }
        """
        .formatted(INCIDENT_ID, INCIDENT_ID);
  }

  private long count(String tableName) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM "
            + tableName
            + " WHERE "
            + ("offline_package_manifest".equals(tableName)
                ? "incident_id = ?"
                : "offline_package_manifest_id = ?"),
        Long.class,
        "offline_package_manifest".equals(tableName) ? INCIDENT_ID.toString() : MANIFEST_ID);
  }

  private List<Map<String, Object>> installationRows() {
    return jdbcTemplate.queryForList(
        """
        SELECT
            status,
            total_item_count,
            completed_item_count,
            failed_item_count,
            failed_item_keys,
            last_error_code,
            last_reported_by_account_id
        FROM offline_package_installation
        WHERE offline_package_manifest_id = ?
        ORDER BY id
        """,
        MANIFEST_ID);
  }

  private List<String> remainingManifestPayloads() {
    return jdbcTemplate.queryForList(
        """
        SELECT manifest_payload
        FROM offline_package_manifest
        WHERE incident_id = ?
        ORDER BY id
        """,
        String.class,
        INCIDENT_ID.toString());
  }

  private static OfflinePackageInstallationReportRequest readyReport() {
    return new OfflinePackageInstallationReportRequest(
        "dev-purge-phone-01",
        MANIFEST_ID,
        1,
        "READY",
        7,
        7,
        0,
        2,
        NOW,
        true,
        List.of(),
        null,
        902,
        0L);
  }

  private static PublishRequest searchAreaChangedEvent() {
    return new PublishRequest(
        UUID.fromString("77777777-0000-4000-8000-0000000009b1"),
        INCIDENT_ID,
        "SEARCH_AREA_CHANGED",
        1,
        "search_area",
        UUID.fromString("77777777-0000-4000-8000-0000000009b2"),
        CLOSED_AT.plusSeconds(60),
        Map.of(
            "id",
            "osa-purge-l6-t09a-revised",
            "incidentId",
            INCIDENT_ID.toString(),
            "version",
            2,
            "overallAreaHash",
            "overall-area-hash-purge-revised"));
  }
}
