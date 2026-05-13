package com.surimap.offlinepackage.fixture;

import java.time.OffsetDateTime;
import java.util.List;

/** L6-T06A SC-09 offline_package_installation status/report fixture. */
public final class OfflinePackageInstallationFixtures {

  public static final String INSTALLATION_ID = "pkg-status-precinct-001";
  public static final String SEEDED_READY_INSTALLATION_ID = "pkg-status-precinct-ready-001";
  public static final String SEEDED_PARTIAL_INSTALLATION_ID = "pkg-status-precinct-partial-001";
  public static final String SEEDED_DOWNLOADING_INSTALLATION_ID =
      "pkg-status-precinct-downloading-001";
  public static final String PACKAGE_STATUS_RESPONSE_ID = "pkg-inc-precinct-first-001";
  public static final String BOARD_ROW_ID = "board-package-inc-precinct-first-001";
  public static final String EVENT_ID = "evt-s7-package-status-001";
  public static final String EVENT_TYPE = "OFFLINE_PACKAGE_INSTALLATION_CHANGED";
  public static final String SEARCH_AREA_CHANGED_EVENT_TYPE = "SEARCH_AREA_CHANGED";
  public static final String SOURCE_ENTITY_TYPE = "offline_package_installation";
  public static final int SEQUENCE = 901;
  public static final int VERSION = 3;
  public static final int STALE_MANIFEST_VERSION =
      OfflinePackageManifestFixtures.MANIFEST_VERSION + 1;
  public static final OffsetDateTime CLIENT_TS = OffsetDateTime.parse("2026-04-28T09:00:40+09:00");
  public static final OffsetDateTime SERVER_TS = OffsetDateTime.parse("2026-04-28T09:00:41+09:00");
  public static final String IDEMPOTENCY_KEY = "idem-package-001";

  public static final String INITIAL_MARKER_ITEM_KEY =
      "initial-marker:" + OfflinePackageManifestFixtures.INITIAL_MARKER_ID;
  public static final String TILE_ITEM_KEY =
      "tile-manifest:" + OfflinePackageManifestFixtures.MANIFEST_ID;

  private OfflinePackageInstallationFixtures() {}

  public static String apiPath() {
    return "/api/incidents/%s/offline-package/installations"
        .formatted(OfflinePackageManifestFixtures.INCIDENT_ID);
  }

  public static String manifestApiPath() {
    return "/api/incidents/%s/offline-package/manifest"
        .formatted(OfflinePackageManifestFixtures.INCIDENT_ID);
  }

  public static String readyReportJson() {
    return """
        {
          "policePhoneId": "%s",
          "manifestId": "%s",
          "manifestVersion": %d,
          "status": "READY",
          "totalItems": 7,
          "completedItems": 7,
          "failedItems": 0,
          "version": %d,
          "clientTs": "%s",
          "readyForOfflineUse": true,
          "failedItemKeys": [],
          "sequence": %d,
          "clockOffsetMs": 0
        }
        """
        .formatted(
            OfflinePackageManifestFixtures.POLICE_PHONE_ID,
            OfflinePackageManifestFixtures.MANIFEST_ID,
            OfflinePackageManifestFixtures.MANIFEST_VERSION,
            VERSION,
            CLIENT_TS,
            SEQUENCE);
  }

  public static String partialReportJson() {
    return """
        {
          "policePhoneId": "%s",
          "manifestId": "%s",
          "manifestVersion": %d,
          "status": "PARTIAL",
          "totalItems": 7,
          "completedItems": 5,
          "failedItems": 2,
          "version": %d,
          "clientTs": "%s",
          "readyForOfflineUse": false,
          "failedItemKeys": [
            "%s",
            "%s"
          ],
          "lastError": "tile-checksum-mismatch",
          "sequence": %d,
          "clockOffsetMs": 0
        }
        """
        .formatted(
            OfflinePackageManifestFixtures.POLICE_PHONE_ID,
            OfflinePackageManifestFixtures.MANIFEST_ID,
            OfflinePackageManifestFixtures.MANIFEST_VERSION,
            VERSION,
            CLIENT_TS,
            INITIAL_MARKER_ITEM_KEY,
            TILE_ITEM_KEY,
            SEQUENCE);
  }

  public static List<OfflinePackageInstallationStatus> byIncidentQueryRows() {
    return List.of(
        status(INSTALLATION_ID, "READY", VERSION, OfflinePackageManifestFixtures.POLICE_PHONE_ID),
        status(SEEDED_PARTIAL_INSTALLATION_ID, "PARTIAL", 2, "dev-precinct-phone-02"),
        status("pkg-status-precinct-stale-001", "STALE", 4, "dev-precinct-phone-03"),
        status("pkg-status-precinct-failed-001", "FAILED", 5, "dev-precinct-phone-04"));
  }

  private static OfflinePackageInstallationStatus status(
      String id, String status, long version, String policePhoneId) {
    return new OfflinePackageInstallationStatus(
        id,
        OfflinePackageManifestFixtures.INCIDENT_ID,
        policePhoneId,
        policePhoneCode(policePhoneId),
        policePhoneName(policePhoneId),
        status,
        version,
        SEQUENCE,
        OfflinePackageManifestFixtures.MANIFEST_VERSION,
        "READY".equals(status));
  }

  public record OfflinePackageInstallationStatus(
      String id,
      String incidentId,
      String policePhoneId,
      String policePhoneCode,
      String policePhoneName,
      String status,
      long version,
      long sequence,
      int manifestVersion,
      boolean readyForOfflineUse) {}

  private static String policePhoneCode(String policePhoneId) {
    if (OfflinePackageManifestFixtures.POLICE_PHONE_ID.equals(policePhoneId)) {
      return OfflinePackageManifestFixtures.POLICE_PHONE_CODE;
    }
    if (SEEDED_PHONE_02_ID.equals(policePhoneId)) return "dev-precinct-phone-02";
    if (SEEDED_PHONE_03_ID.equals(policePhoneId)) return "dev-precinct-phone-03";
    if (SEEDED_PHONE_04_ID.equals(policePhoneId)) return "dev-precinct-phone-04";
    if (SEEDED_PHONE_05_ID.equals(policePhoneId)) return "dev-precinct-phone-05";
    return policePhoneId;
  }

  private static String policePhoneName(String policePhoneId) {
    if (OfflinePackageManifestFixtures.POLICE_PHONE_ID.equals(policePhoneId)) {
      return "경찰서 팀폰";
    }
    if (SEEDED_PHONE_02_ID.equals(policePhoneId)) return "경찰서 팀폰 02";
    if (SEEDED_PHONE_03_ID.equals(policePhoneId)) return "경찰서 팀폰 03";
    if (SEEDED_PHONE_04_ID.equals(policePhoneId)) return "경찰서 팀폰 04";
    if (SEEDED_PHONE_05_ID.equals(policePhoneId)) return "경찰서 팀폰 05";
    return policePhoneCode(policePhoneId);
  }
}
