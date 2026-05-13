package com.surimap.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.offlinepackage.query.OfflinePackageInstallationQuery;
import com.surimap.retention.purge.PurgeHook;
import com.surimap.retention.purge.PurgeHookName;
import com.surimap.retention.purge.PurgeHookRequest;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** L6-T09B RED: SC-12 terminal/package board state must stay sanitized after purge. */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("L6-T09B incident_terminal/package_badge privacy board state RED")
class IncidentTerminalPackageBadgePrivacyRedTest {

  private static final UUID INCIDENT_ID = UUID.fromString("77777777-0000-4000-8000-0000000009b1");
  private static final UUID PURGE_RUN_ID = UUID.fromString("77777777-0000-4000-8000-0000000009b2");
  private static final Instant CLOSED_AT = Instant.parse("2026-05-07T00:10:00Z");
  private static final Instant PURGE_DEADLINE_TS = Instant.parse("2026-05-08T00:10:00Z");
  private static final OffsetDateTime SERVER_TS = OffsetDateTime.parse("2026-05-07T09:11:00+09:00");
  private static final String MANIFEST_ID = "77777777-0000-4000-8000-0000000009d1";
  private static final String OP_ID = "77777777-0000-4000-8000-0000000009d2";
  private static final String OVERALL_SEARCH_AREA_ID = "77777777-0000-4000-8000-0000000009d3";
  private static final String LAST_REPORTED_BY_ACCOUNT_ID = "11111111-1111-1111-1111-111111119903";
  private static final List<String> PRE_PURGE_STATUSES = List.of("READY", "PARTIAL", "STALE");
  private static final List<String> TERMINAL_RESPONSE_KEYS =
      List.of(
          "id",
          "status",
          "version",
          "sequence",
          "sourceSpec",
          "sourceHash",
          "latestEventId",
          "incidentId",
          "terminalStatus",
          "closedStatus",
          "closedAt",
          "writeDisabledReason",
          "localPurgeState");

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private OfflinePackageInstallationQuery installationQuery;

  @Autowired private List<PurgeHook> purgeHooks;

  @BeforeEach
  void resetPackageTables() {
    jdbcTemplate.update("DELETE FROM offline_package_installation");
    jdbcTemplate.update("DELETE FROM offline_package_manifest");
  }

  @Test
  @DisplayName("closed/purged board response exposes incident_terminal tombstone fields only")
  void closedPurgedBoardResponseExposesIncidentTerminalTombstoneFieldsOnly() {
    BoardDTO board = boardWithRows(List.of(terminalRowWithForbiddenSourceFields()));

    @SuppressWarnings("unchecked")
    Map<String, Object> terminal = (Map<String, Object>) board.slots().get("incident_terminal");

    assertThat(terminal.keySet()).containsExactlyInAnyOrderElementsOf(TERMINAL_RESPONSE_KEYS);
    assertThat(terminal)
        .containsEntry("incidentId", INCIDENT_ID.toString())
        .containsEntry("terminalStatus", "PURGED")
        .containsEntry("closedStatus", "purged")
        .containsEntry("closedAt", "2026-05-07T09:10:00+09:00")
        .containsEntry("writeDisabledReason", "purged")
        .containsEntry("localPurgeState", "completed");
    assertThat(board.slotSources().get("incident_terminal"))
        .singleElement()
        .satisfies(
            cursor -> {
              assertThat(cursor.id()).isEqualTo(INCIDENT_ID.toString());
              assertThat(cursor.status()).isEqualTo("PURGED");
              assertThat(cursor.version()).isEqualTo(12L);
              assertThat(cursor.sequence()).isEqualTo(912L);
              assertThat(cursor.sourceSpec()).isEqualTo("S1-3");
            });
    assertThat(serializedSlots(board))
        .doesNotContain(
            "missing_person",
            "missingPerson",
            "displayName",
            "photoObjectKey",
            "lastSeenLocationText",
            "latestLocation",
            "packageReloadUrl",
            "streamResubscribeHint",
            "resubscribe",
            "reload");
  }

  @Test
  @DisplayName(
      "package_badge rows after PackagePurgeHook expose only sanitized PURGED package state")
  void purgedPackageBadgeRowsExposeOnlySanitizedPackageState() {
    seedPackageRowsWithMissingPersonPayload();
    packagePurgeHook().purge(purgeRequest());

    BoardDTO board = boardWithRows(packageBadgeSourceRows());

    List<Map<String, Object>> packageRows = packageBadgeRows(board);
    assertThat(packageRows).hasSize(PRE_PURGE_STATUSES.size());
    assertThat(packageRows)
        .allSatisfy(
            row -> {
              assertThat(row)
                  .containsEntry("status", "PURGED")
                  .containsEntry("packageStatus", "PURGED");
              assertThat(row)
                  .doesNotContainKeys(
                      "manifestVersion",
                      "activeManifestVersion",
                      "readyForOfflineUse",
                      "localWarningInput",
                      "packageItems",
                      "missingPerson",
                      "missing_person",
                      "reloadReason",
                      "reloadUrl",
                      "staleRefetch");
              assertThat(String.valueOf(row))
                  .doesNotContain("READY", "PARTIAL", "STALE", "MISSING_PERSON_CACHE", "가상 실종자");
            });
  }

  @Test
  @DisplayName("SC-12 terminal/package board payloads do not leak missing_person PII after purge")
  void terminalAndPackagePayloadsDoNotLeakMissingPersonPiiAfterPurge() {
    seedPackageRowsWithMissingPersonPayload();
    packagePurgeHook().purge(purgeRequest());

    BoardDTO board =
        boardWithRows(
            concat(List.of(terminalRowWithForbiddenSourceFields()), packageBadgeSourceRows()));

    assertThat(serializedSlots(board))
        .doesNotContain(
            "missing_person",
            "missingPerson",
            "MISSING_PERSON_CACHE",
            "가상 실종자",
            "photo/missing-person/purge-target.jpg",
            "lastSeenLocationText",
            "인왕산 북측 산책로",
            "latestLocation",
            "packageReloadUrl",
            "streamResubscribeHint");
  }

  private BoardDTO boardWithRows(List<BoardSourceRow> rows) {
    return new BoardAssembler()
        .assemble(
            new BoardAssemblyRequest(
                INCIDENT_ID.toString(),
                "board-response-l6-t09b-terminal-package",
                12L,
                SERVER_TS,
                null,
                List.of(),
                "hash-board-terminal-purged",
                rows));
  }

  private List<BoardSourceRow> packageBadgeSourceRows() {
    return new PackageBadgeBoardAssembler(installationQuery)
        .sourceRowsByIncident(INCIDENT_ID.toString());
  }

  private PurgeHook packagePurgeHook() {
    List<PurgeHook> hooks =
        purgeHooks.stream().filter(hook -> hook.name() == PurgeHookName.OFFLINE_PACKAGE).toList();
    assertThat(hooks)
        .as("S7 PackagePurgeHook must be exposed through the S1-3 hook boundary")
        .hasSize(1);
    return hooks.get(0);
  }

  private static PurgeHookRequest purgeRequest() {
    return new PurgeHookRequest(INCIDENT_ID, PURGE_RUN_ID, CLOSED_AT, PURGE_DEADLINE_TS);
  }

  private void seedPackageRowsWithMissingPersonPayload() {
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
        VALUES (?, ?, 1, ?, ?, 1,
                'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 1,
                ?, ?, ?, ?)
        """,
        MANIFEST_ID,
        INCIDENT_ID.toString(),
        OP_ID,
        OVERALL_SEARCH_AREA_ID,
        manifestPayloadWithMissingPerson(),
        SERVER_TS.plusHours(24),
        SERVER_TS,
        SERVER_TS);

    int index = 0;
    for (String status : PRE_PURGE_STATUSES) {
      seedInstallation(status, ++index);
    }
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
        VALUES (?, ?, ?, ?, ?, 7, 3, 2, NULL, 'tile-timeout', ?, 1, ?, ?)
        """,
        installationId(index),
        MANIFEST_ID,
        policePhoneId(index),
        LAST_REPORTED_BY_ACCOUNT_ID,
        status,
        SERVER_TS,
        SERVER_TS,
        SERVER_TS);
  }

  private static String installationId(int index) {
    return "77777777-0000-4000-8000-0000000009%02d".formatted(index + 30);
  }

  private static String policePhoneId(int index) {
    return "00000000-0000-0000-0000-0000000098%02d".formatted(index);
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

  private static BoardSourceRow terminalRowWithForbiddenSourceFields() {
    return new BoardSourceRow(
        "incident_terminal",
        "S1-3",
        INCIDENT_ID.toString(),
        "board-incident-terminal-l6-t09b",
        "PURGED",
        12L,
        912L,
        "evt-s1-3-incident-purged-l6-t09b",
        "hash-s1-3-terminal-purged",
        Map.ofEntries(
            Map.entry("incidentId", INCIDENT_ID.toString()),
            Map.entry("terminalStatus", "PURGED"),
            Map.entry("closedStatus", "purged"),
            Map.entry("closedAt", "2026-05-07T09:10:00+09:00"),
            Map.entry("writeDisabledReason", "purged"),
            Map.entry("localPurgeState", "completed"),
            Map.entry("missing_person", Map.of("displayName", "가상 실종자")),
            Map.entry(
                "missingPerson", Map.of("photoObjectKey", "photo/missing-person/purge-target.jpg")),
            Map.entry("lastSeenLocationText", "인왕산 북측 산책로"),
            Map.entry(
                "latestLocation",
                Map.of("type", "Point", "coordinates", List.of(126.9565, 37.5712))),
            Map.entry("packageReloadUrl", "/api/incidents/" + INCIDENT_ID + "/packages/reload"),
            Map.entry("streamResubscribeHint", "/api/incidents/" + INCIDENT_ID + "/events")));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> packageBadgeRows(BoardDTO board) {
    return (List<Map<String, Object>>) board.slots().get("package_badge");
  }

  private static String serializedSlots(BoardDTO board) {
    return String.valueOf(board.slots());
  }

  private static List<BoardSourceRow> concat(
      List<BoardSourceRow> first, List<BoardSourceRow> second) {
    return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
  }
}
