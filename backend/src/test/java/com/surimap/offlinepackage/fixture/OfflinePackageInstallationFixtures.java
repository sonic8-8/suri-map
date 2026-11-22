package com.surimap.offlinepackage.fixture;

import java.time.OffsetDateTime;

/** L6-T06A SC-09 offline_package_installation status/report fixture. */
public final class OfflinePackageInstallationFixtures {

  public static final String INSTALLATION_ALIAS = "pkg-status-precinct-001";
  public static final String INSTALLATION_ID = "77777777-0000-4000-8000-000000000901";
  public static final String SEEDED_READY_INSTALLATION_ALIAS = "pkg-status-precinct-ready-001";
  public static final String SEEDED_READY_INSTALLATION_ID =
      "7d200ffb-55a0-67c5-d313-acad39406c53";
  public static final String SEEDED_PARTIAL_INSTALLATION_ALIAS = "pkg-status-precinct-partial-001";
  public static final String SEEDED_PARTIAL_INSTALLATION_ID =
      "6b99e580-a17d-aaff-6df7-a4e52df73e86";
  public static final String SEEDED_DOWNLOADING_INSTALLATION_ALIAS =
      "pkg-status-precinct-downloading-001";
  public static final String SEEDED_DOWNLOADING_INSTALLATION_ID =
      "34add8d1-8695-471f-925a-7ae8c7293a3f";
  public static final String SEEDED_PHONE_02_ID = "0cc358d9-32c1-e329-d254-5446302ee2a8";
  public static final String SEEDED_PHONE_05_ID = "7331df5a-da39-c0bb-2aa9-97c2040e49e1";
  // 설치 보고 저장과 STALE 전환 뒤에 이 이벤트가 발행되지 않는지 확인할 때 쓴다.
  public static final String EVENT_TYPE = "OFFLINE_PACKAGE_INSTALLATION_CHANGED";
  public static final String SEARCH_AREA_CHANGED_EVENT_TYPE = "SEARCH_AREA_CHANGED";
  public static final int SEQUENCE = 901;
  public static final int VERSION = 3;
  public static final int STALE_MANIFEST_VERSION =
      OfflinePackageManifestFixtures.MANIFEST_VERSION + 1;
  public static final String STALE_MANIFEST_ID = "b1adca4f-cb23-4539-18bf-d2045ad0ffe4";
  public static final OffsetDateTime CLIENT_TS = OffsetDateTime.parse("2026-04-28T09:00:40+09:00");
  public static final OffsetDateTime SERVER_TS = OffsetDateTime.parse("2026-04-28T09:00:41+09:00");
  public static final String IDEMPOTENCY_KEY = "idem-package-001";

  public static final String INITIAL_MARKER_ITEM_KEY =
      "initial-marker:" + OfflinePackageManifestFixtures.INITIAL_MARKER_ID;
  public static final String TILE_ITEM_KEY =
      "tile-manifest:" + OfflinePackageManifestFixtures.MANIFEST_ALIAS;

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
}
